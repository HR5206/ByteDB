public class BloomFilter {
    private final long[] bits;
    private final int numHashFunctions;
    private final int size;

    public BloomFilter(int expectedElements, double falsePositiveRate) {
        this.size = (int) Math.ceil(-(expectedElements * Math.log(falsePositiveRate)) / (Math.log(2) * Math.log(2)));
        int numLongs = (size + 63) / 64;
        bits = new long[numLongs];
        this.numHashFunctions = Math.max(1, (int) Math.round((size / (double) expectedElements) * Math.log(2)));
    }

    private int[] hash(String key) {
        int[] hs = new int[numHashFunctions];
        int h1 = key.hashCode();
        int h2 = key.length(); // simple secondary hash
        for (int i = 0; i < numHashFunctions; i++) {
            long combined = (long) h1 + i * h2;
            hs[i] = (int) (Math.abs(combined) % size);
        }
        return hs;
    }

    public void add(String key) {
        for (int pos : hash(key)) {
            int idx = pos >>> 6; // divide by 64
            int bit = pos & 63;
            bits[idx] |= (1L << bit);
        }
    }

    public boolean mightContain(String key) {
        for (int pos : hash(key)) {
            int idx = pos >>> 6;
            int bit = pos & 63;
            if ((bits[idx] & (1L << bit)) == 0) return false;
        }
        return true;
    }

    // Serialize to byte[] (little‑endian longs)
    public byte[] toBytes() {
        byte[] out = new byte[bits.length * 8];
        for (int i = 0; i < bits.length; i++) {
            long v = bits[i];
            for (int b = 0; b < 8; b++) {
                out[i * 8 + b] = (byte) (v & 0xFF);
                v >>>= 8;
            }
        }
        return out;
    }

    public static BloomFilter fromBytes(byte[] data, int expectedElements, double fpr) {
        BloomFilter bf = new BloomFilter(expectedElements, fpr);
        if (data.length != bf.bits.length * 8) throw new IllegalArgumentException("Invalid bloom size");
        for (int i = 0; i < bf.bits.length; i++) {
            long v = 0;
            for (int b = 7; b >= 0; b--) {
                v = (v << 8) | (data[i * 8 + b] & 0xFF);
            }
            bf.bits[i] = v;
        }
        return bf;
    }
}
