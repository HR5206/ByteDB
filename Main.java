public class Main {
    public static void main(String[] args) throws Exception {
        MemTable mem = new MemTable("data/wal.log");
        // Simple demo workload
        long start = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            mem.put("key" + i, "value" + i);
        }
        long putTime = System.nanoTime() - start;
        System.out.printf("Inserted %d entries in %.3f ms (%.2f ops/sec)%n",
                mem.size(), putTime / 1_000_000.0, mem.size() / (putTime / 1e9));

        // Random reads benchmark
        java.util.Random rnd = new java.util.Random();
        start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            String v = mem.get("key" + rnd.nextInt(100_000));
            if (v == null) throw new AssertionError("Missing value");
        }
        long getTime = System.nanoTime() - start;
        System.out.printf("Performed 10,000 reads in %.3f ms (%.2f ops/sec)%n",
                getTime / 1_000_000.0, 10_000 / (getTime / 1e9));
    }
}
