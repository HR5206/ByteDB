import java.util.*;
import java.nio.file.*;

public class DemoRangeScan {
    public static void main(String[] args) throws Exception {
        ByteDB db = new ByteDB();
        // populate 0‑200k keys (same as earlier demo)
        for (int i = 0; i < 200_000; i++) db.put("key" + i, "val" + i);
        // ensure a flush so we have at least one SSTable
        db.flushMemTable();
        // collect SSTable paths
        List<String> sst = new ArrayList<>();
        for (java.nio.file.Path p : java.nio.file.Files.newDirectoryStream(Paths.get("data/sst"), "*.sst")) {
            sst.add(p.toString());
        }
        // range scan
        RangeScanner scanner = new RangeScanner(db.memTable, sst, "key1000", "key1050");
        System.out.println("Scanning range [key1000, key1050):");
        while (scanner.hasNext()) {
            var e = scanner.next();
            System.out.println(e.getKey() + " => " + e.getValue());
        }
        db.shutdown();
    }
}
