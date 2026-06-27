import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

public class SSTableReader {
    private static final int HEADER_SIZE = 16;
    private static final int MAGIC = 0x534b5458;

    private final Path filePath;
    private final FileChannel channel;
    private final List<Long> indexOffsets = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    private final BloomFilter bloom;

    public SSTableReader(String fileName) throws IOException {
        this.filePath = Paths.get(fileName);
        this.channel = FileChannel.open(filePath, StandardOpenOption.READ);
        // read header
        ByteBuffer hdr = ByteBuffer.allocate(HEADER_SIZE);
        channel.read(hdr, 0);
        hdr.flip();
        int magic = hdr.getInt();
        if (magic != MAGIC) throw new IOException("Invalid SSTable magic");
        hdr.getInt(); // version ignored for now
        long indexPos = hdr.getLong();

        // read index
        channel.position(indexPos);
        while (true) {
            ByteBuffer keyLenBuf = ByteBuffer.allocate(4);
            int read = channel.read(keyLenBuf);
            if (read == -1) break; // EOF
            if (read < 4) throw new EOFException();
            keyLenBuf.flip();
            int klen = keyLenBuf.getInt();
            ByteBuffer keyBuf = ByteBuffer.allocate(klen + 8);
            channel.read(keyBuf);
            keyBuf.flip();
            byte[] kbytes = new byte[klen];
            keyBuf.get(kbytes);
            long offset = keyBuf.getLong();
            keys.add(new String(kbytes, "UTF-8"));
            indexOffsets.add(offset);
        }
        // bloom is stored just before the index; we locate it by reading backwards from the first index entry
        long firstIdxOffset = indexOffsets.get(0);
        // read bloom length (int) located at firstIdxOffset - 4 - bloomLenBytes
        ByteBuffer lenBuf = ByteBuffer.allocate(4);
        channel.read(lenBuf, firstIdxOffset - 4);
        lenBuf.flip();
        int bloomLen = lenBuf.getInt();
        ByteBuffer bloomBuf = ByteBuffer.allocate(bloomLen);
        channel.read(bloomBuf, firstIdxOffset - 4 - bloomLen);
        bloomBuf.flip();
        byte[] bloomBytes = new byte[bloomLen];
        bloomBuf.get(bloomBytes);
        // we don't know exact expectedElements, but we can reconstruct with same false‑positive rate (0.01)
        this.bloom = BloomFilter.fromBytes(bloomBytes, keys.size(), 0.01);
    }

    public String get(String key) throws IOException {
        if (!bloom.mightContain(key)) return null;
        // binary search over keys list
        int lo = 0, hi = keys.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int cmp = keys.get(mid).compareTo(key);
            if (cmp == 0) {
                long offset = indexOffsets.get(mid);
                // read entry at offset
                ByteBuffer lenBuf = ByteBuffer.allocate(4);
                channel.read(lenBuf, offset);
                lenBuf.flip();
                int klen = lenBuf.getInt();
                // skip key bytes (they are known)
                long valuePos = offset + 4 + klen + 4; // after keyLen, key, valLen
                ByteBuffer valLenBuf = ByteBuffer.allocate(4);
                channel.read(valLenBuf, offset + 4 + klen);
                valLenBuf.flip();
                int vlen = valLenBuf.getInt();
                ByteBuffer valBuf = ByteBuffer.allocate(vlen);
                channel.read(valBuf, valuePos);
                valBuf.flip();
                byte[] vbytes = new byte[vlen];
                valBuf.get(vbytes);
                return new String(vbytes, "UTF-8");
            } else if (cmp < 0) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return null;
    }

    public void close() throws IOException { channel.close(); }
}
