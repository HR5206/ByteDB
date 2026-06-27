import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class CompactionManager {
    private final Path sstDir;                 // directory holding .sst files
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    private final int maxSSTablesPerCompaction;

    public CompactionManager(String dir, int maxSSTablesPerCompaction) throws IOException {
        this.sstDir = Paths.get(dir);
        Files.createDirectories(sstDir);
        this.maxSSTablesPerCompaction = maxSSTablesPerCompaction;
        // schedule compaction every 30 seconds (demo purpose)
        exec.scheduleAtFixedRate(this::compactIfNeeded, 30, 30, TimeUnit.SECONDS);
    }

    private void compactIfNeeded() {
        try {
            List<Path> sstFiles = listSSTables();
            if (sstFiles.size() <= maxSSTablesPerCompaction) return; // nothing to do
            // pick the oldest `maxSSTablesPerCompaction` files
            List<Path> toMerge = sstFiles.subList(0, maxSSTablesPerCompaction);
            mergeFiles(toMerge);
        } catch (Exception e) {
            System.err.println("Compaction error: " + e.getMessage());
        }
    }

    private List<Path> listSSTables() throws IOException {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(sstDir, "*.sst")) {
            List<Path> list = new ArrayList<>();
            for (Path p : ds) list.add(p);
            // sort by creation time (oldest first)
            list.sort(Comparator.comparingLong(p -> p.toFile().lastModified()));
            return list;
        }
    }

    private void mergeFiles(List<Path> sources) throws IOException {
        // Load readers for each source (assume they are already sorted)
        List<SSTableReader> readers = new ArrayList<>();
        for (Path p : sources) readers.add(new SSTableReader(p.toString()));

        // simple k‑way merge using a priority queue on the current key of each reader
        PriorityQueue<MergeEntry> pq = new PriorityQueue<>(Comparator.comparing(e -> e.key));
        for (int i = 0; i < readers.size(); i++) {
            String key = readers.get(i).nextKey(); // custom method to get first key (see note below)
            if (key != null) pq.add(new MergeEntry(i, key, readers.get(i).currentValue()));
        }

        // destination file (temporary name)
        Path tmp = sstDir.resolve("merged_" + System.currentTimeMillis() + ".sst.tmp");
        SSTableWriter writer = new SSTableWriter(tmp.toString(), readers.size() * 1000); // estimate

        String lastKey = null;
        while (!pq.isEmpty()) {
            MergeEntry e = pq.poll();
            // keep the latest value for a key (readers are ordered by creation time, newer files are later in the list)
            if (!e.key.equals(lastKey)) {
                // write the key/value unless it is a tombstone (null)
                if (e.value != null) writer.writeEntry(e.key, e.value);
                lastKey = e.key;
            }
            // advance the reader that supplied the entry
            SSTableReader r = readers.get(e.readerIdx);
            if (r.advance()) { // moves to next key, returns true if exists
                pq.add(new MergeEntry(e.readerIdx, r.currentKey(), r.currentValue()));
            }
        }
        writer.close();
        // replace old files atomically
        Path finalPath = sstDir.resolve("merged_" + System.currentTimeMillis() + ".sst");
        Files.move(tmp, finalPath, StandardCopyOption.ATOMIC_MOVE);
        // delete source files
        for (Path p : sources) Files.deleteIfExists(p);
        // close readers
        for (SSTableReader r : readers) r.close();
        System.out.println("Compaction produced " + finalPath.getFileName());
    }

    // Helper class for priority‑queue entries
    private static class MergeEntry {
        final int readerIdx;
        final String key;
        final String value; // null => tombstone
        MergeEntry(int i, String k, String v) { this.readerIdx = i; this.key = k; this.value = v; }
    }

    public void shutdown() { exec.shutdownNow(); }
}
