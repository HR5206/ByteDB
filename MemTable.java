import java.io.IOException;

public class MemTable {
    SkipList<String, String> skiplist = new SkipList<>();
    WriteAheadLog wal;

    public MemTable(String walFile) throws IOException { this.wal = new WriteAheadLog(walFile); }

    public void put(String key, String value) throws IOException {
        wal.append(key, value);
        skiplist.put(key, value);
    }

    public String get(String key) { return skiplist.get(key); }

    public int size() { return skiplist.size(); }
}
