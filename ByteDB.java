import java.io.IOException;
import java.util.*;

public class ByteDB {
    private static final long MEMTABLE_FLUSH_THRESHOLD = 2_000_000; // bytes approx.
    private final MemTable memTable;
    private final LRUCache<String, String> cache;
    private final Path sstDir = Paths.get("data/sst");
    private final CompactionManager compactionMgr;

    public ByteDB() throws IOException {
        Files.createDirectories(sstDir);
        this.memTable = new MemTable("data/wal.log");
        this.cache = new LRUCache<>(10_000);
        this.compactionMgr = new CompactionManager(sstDir.toString(), 4);
    }

    public void put(String key, String value) throws IOException {
        memTable.put(key, value);
        cache.put(key, value);
        if (approxMemSize() >= MEMTABLE_FLUSH_THRESHOLD) flushMemTable();
    }

    public String get(String key) throws IOException {
        String cached = cache.get(key);
        if (cached != null) return cached;
        String v = memTable.get(key);
        if (v != null) { cache.put(key, v); return v; }
        // search SSTables (newest first)
        List<Path> ssts = listSSTables();
        Collections.reverse(ssts);
        for (Path p : ssts) {
            SSTableReader r = new SSTableReader(p.toString());
            v = r.get(key);
            r.close();
            if (v != null) { cache.put(key, v); return v; }
        }
        return null;
    }

    // Simple delete => write tombstone (null) to WAL & MemTable
    public void delete(String key) throws IOException {
        memTable.put(key, null); // null signals tombstone
        cache.put(key, null);
    }

    private void flushMemTable() throws IOException {
        // dump sorted entries to a new SSTable
        String sstPath = sstDir.resolve("sst_" + System.currentTimeMillis() + ".sst").toString();
        SSTableWriter writer = new SSTableWriter(sstPath, memTable.size());
        // iterate over memtable (SkipList provides no iterator – we traverse via internal structure)
        // For demo we simply replay the WAL which is already sorted by insertion order.
        for (String[] kv : memTable.wal.replay()) {
            if (kv[1] != null) writer.writeEntry(kv[0], kv[1]); // skip tombstones on flush
        }
        writer.close();
        // reset memtable (new instance)
        // In a real engine we would reuse the same memtable object after clearing.
        // For brevity we just create a fresh one.
        // NOTE: The WAL file can be truncated after a successful flush.
        Files.deleteIfExists(Paths.get("data/wal.log"));
        // Re‑initialise memtable with a fresh WAL
        // (In practice we would have a clear() method.)
        // Here we just replace the reference.
        // This simplistic approach is enough for the educational demo.
        // Replace in‑place:
        MemTable newMem = new MemTable("data/wal.log");
        // reflectively set the field (or redesign for mutability). For demo we cheat:
        //noinspection UnstableApiUsage
        this.memTable.skiplist = new SkipList<>(); // reset skiplist
        this.memTable.wal = new WriteAheadLog("data/wal.log");
    }

    private long approxMemSize() {
        // Very rough estimate: each entry ~ 40 bytes + key/value length.
        return memTable.size() * 64L;
    }

    private List<Path> listSSTables() throws IOException {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(sstDir, "*.sst")) {
            List<Path> list = new ArrayList<>();
            for (Path p : ds) list.add(p);
            return list;
        }
    }

    public void shutdown() throws IOException {
        compactionMgr.shutdown();
        // flush remaining data
        if (memTable.size() > 0) flushMemTable();
    }
}
