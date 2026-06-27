import java.io.*;
import java.util.*;
import java.nio.file.*;

public class ByteDBCLI {
    private static final ByteDB DB;
    static {
        try { DB = new ByteDB(); } catch (IOException e) { throw new IllegalStateException(e); }
        // ensure DB is closed on JVM exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { DB.shutdown(); } catch (IOException ignored) {}
        }));
    }

    public static void main(String[] args) throws Exception {
        System.out.println("ByteDB CLI – type HELP for commands");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.equalsIgnoreCase("EXIT") || line.equalsIgnoreCase("QUIT")) break;
            if (line.equalsIgnoreCase("HELP")) { printHelp(); continue; }
            long start = System.nanoTime();
            try {
                handleCommand(line);
            } catch (Exception ex) {
                System.err.println("Error: " + ex.getMessage());
            }
            long elapsedNs = System.nanoTime() - start;
            double secs = elapsedNs / 1e9;
            System.out.printf("[%.3f ms (%.2f ops/sec)]%n", elapsedNs / 1_000_000.0, 1.0 / secs);
        }
        System.out.println("Bye!");
    }

    private static void handleCommand(String cmd) throws Exception {
        String[] parts = cmd.split("\\s+", 3);
        String op = parts[0].toUpperCase();
        switch (op) {
            case "PUT":
                if (parts.length != 3) throw new IllegalArgumentException("PUT requires key and value");
                DB.put(parts[1], parts[2]);
                System.out.println("OK");
                break;
            case "GET":
                if (parts.length != 2) throw new IllegalArgumentException("GET requires key");
                String val = DB.get(parts[1]);
                System.out.println(val != null ? val : "(null)");
                break;
            case "DELETE":
                if (parts.length != 2) throw new IllegalArgumentException("DELETE requires key");
                DB.delete(parts[1]);
                System.out.println("DELETED");
                break;
            case "SCAN":
                // SCAN startKey endKey (both inclusive) – empty end means scan to end
                if (parts.length < 2) throw new IllegalArgumentException("SCAN requires at least start key");
                String startKey = parts[1];
                String endKey = (parts.length == 3) ? parts[2] : null;
                // Gather SSTable paths for the scanner
                List<String> sstPaths = new ArrayList<>();
                try (var ds = java.nio.file.Files.newDirectoryStream(Paths.get("data/sst"), "*.sst")) {
                    for (var p : ds) sstPaths.add(p.toString());
                }
                RangeScanner scanner = new RangeScanner(DB.memTable, sstPaths, startKey, endKey);
                while (scanner.hasNext()) {
                    var e = scanner.next();
                    System.out.println(e.getKey() + " => " + e.getValue());
                }
                break;
            default:
                System.out.println("Unknown command. Type HELP.");
        }
    }

    private static void printHelp() {
        System.out.println("Available commands:");
        System.out.println("  PUT <key> <value>   – insert or update a key");
        System.out.println("  GET <key>           – retrieve a value");
        System.out.println("  DELETE <key>        – delete a key (tombstone)");
        System.out.println("  SCAN <start> [end]  – iterate keys in range");
        System.out.println("  HELP                – this help message");
        System.out.println("  EXIT / QUIT         – terminate the CLI");
    }
}
