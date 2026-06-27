public class DemoCache {
    public static void main(String[] args) throws Exception {
        ByteDB db = new ByteDB();
        // Populate with 200k keys
        for (int i = 0; i < 200_000; i++) {
            db.put("k" + i, "v" + i);
        }
        // Benchmark cache hit rate
        long start = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            String v = db.get("k" + (i % 20_000)); // hot subset
            if (v == null) throw new AssertionError("missing");
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("100k reads (20k hot keys) in %.3f ms (%.2f ops/sec)%n",
                elapsed / 1_000_000.0, 100_000 / (elapsed / 1e9));
        db.shutdown();
    }
}
