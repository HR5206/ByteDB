import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.nio.file.*;

public class SSTableWriter {
    private static final int HEADER_SIZE = 16; // magic (4) + version (4) + index offset (8)
    private static final int MAGIC = 0x534b5458; // "SKTX"
    private static final int VERSION = 1;

    private final Path filePath;
    private final List<Long> indexOffsets = new ArrayList<>(); // file offset for each key
    private final List<String> keys = new ArrayList<>();
    private final BloomFilter bloom;
    private final FileChannel channel;
    private long dataStart;

    public SSTableWriter(String fileName, int expectedEntries) throws IOException {
        this.filePath = Paths.get(fileName);
        Files.createDirectories(filePath.getParent());
        this.channel = FileChannel.open(filePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        // reserve header space
        channel.position(HEADER_SIZE);
        this.bloom = new BloomFilter(expectedEntries, 0.01);
    }

    // called with entries in *sorted* order
    public void writeEntry(String key, String value) throws IOException {
        byte[] keyBytes = key.getBytes("UTF-8");
        byte[] valBytes = value.getBytes("UTF-8");
        ByteBuffer buf = ByteBuffer.allocate(4 + keyBytes.length + 4 + valBytes.length);
        buf.putInt(keyBytes.length);
        buf.put(keyBytes);
        buf.putInt(valBytes.length);
        buf.put(valBytes);
        buf.flip();
        long pos = channel.position();
        channel.write(buf);
        indexOffsets.add(pos);
        keys.add(key);
        bloom.add(key);
    }

    public void close() throws IOException {
        // write bloom filter length + data
        byte[] bloomBytes = bloom.toBytes();
        ByteBuffer bfBuf = ByteBuffer.allocate(4 + bloomBytes.length);
        bfBuf.putInt(bloomBytes.length);
        bfBuf.put(bloomBytes);
        bfBuf.flip();
        long bloomPos = channel.position();
        channel.write(bfBuf);

        // write index (key length + key + offset) for each entry
        long indexPos = channel.position();
        for (int i = 0; i < keys.size(); i++) {
            byte[] k = keys.get(i).getBytes("UTF-8");
            ByteBuffer ib = ByteBuffer.allocate(4 + k.length + 8);
            ib.putInt(k.length);
            ib.put(k);
            ib.putLong(indexOffsets.get(i));
            ib.flip();
            channel.write(ib);
        }

        // finally write header with index offset
        channel.position(0);
        ByteBuffer hdr = ByteBuffer.allocate(HEADER_SIZE);
        hdr.putInt(MAGIC);
        hdr.putInt(VERSION);
        hdr.putLong(indexPos);
        hdr.flip();
        channel.write(hdr);
        channel.close();
    }
}
