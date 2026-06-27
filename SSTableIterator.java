import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

public class SSTableIterator implements Iterator<Map.Entry<String, String>> {
    private final FileChannel channel;
    private long position; // byte offset of next entry
    private Map.Entry<String, String> nextEntry;

    public SSTableIterator(String filePath, long startOffset) throws IOException {
        this.channel = FileChannel.open(Paths.get(filePath), StandardOpenOption.READ);
        this.position = startOffset;
        advance();
    }

    private void advance() throws IOException {
        if (position < 0) { nextEntry = null; return; }
        ByteBuffer lenBuf = ByteBuffer.allocate(4);
        int read = channel.read(lenBuf, position);
        if (read < 4) { nextEntry = null; return; }
        lenBuf.flip();
        int klen = lenBuf.getInt();
        ByteBuffer keyBuf = ByteBuffer.allocate(klen);
        channel.read(keyBuf, position + 4);
        keyBuf.flip();
        byte[] kbytes = new byte[klen];
        keyBuf.get(kbytes);
        String key = new String(kbytes, "UTF-8");

        ByteBuffer valLenBuf = ByteBuffer.allocate(4);
        channel.read(valLenBuf, position + 4 + klen);
        valLenBuf.flip();
        int vlen = valLenBuf.getInt();
        ByteBuffer valBuf = ByteBuffer.allocate(vlen);
        channel.read(valBuf, position + 4 + klen + 4);
        valBuf.flip();
        byte[] vbytes = new byte[vlen];
        valBuf.get(vbytes);
        String value = new String(vbytes, "UTF-8");

        nextEntry = new AbstractMap.SimpleImmutableEntry<>(key, value);
        // compute position of next entry
        position = position + 4 + klen + 4 + vlen;
    }

    @Override
    public boolean hasNext() { return nextEntry != null; }

    @Override
    public Map.Entry<String, String> next() {
        if (!hasNext()) throw new NoSuchElementException();
        Map.Entry<String, String> cur = nextEntry;
        try { advance(); } catch (IOException e) { throw new UncheckedIOException(e); }
        return cur;
    }

    public void close() throws IOException { channel.close(); }
}
