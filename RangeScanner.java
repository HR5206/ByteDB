import java.io.IOException;
import java.util.*;

public class RangeScanner implements Iterator<Map.Entry<String, String>> {
    private final PriorityQueue<SourceEntry> pq;
    private final String endKey; // exclusive bound; null means no bound

    private static class SourceEntry {
        final Iterator<Map.Entry<String, String>> iter;
        Map.Entry<String, String> cur;
        SourceEntry(Iterator<Map.Entry<String, String>> it) { this.iter = it; advance(); }
        void advance() { cur = iter.hasNext() ? iter.next() : null; }
    }

    public RangeScanner(MemTable mem, List<String> sstPaths, String startKey, String endKey) throws IOException {
        this.endKey = endKey;
        this.pq = new PriorityQueue<>(Comparator.comparing(e -> e.cur.getKey()));
        // 1) MemTable iterator starting at startKey
        Iterator<Map.Entry<String, String>> memIter = mem.skiplist.iteratorFrom(startKey);
        SourceEntry memSrc = new SourceEntry(memIter);
        if (memSrc.cur != null) pq.add(memSrc);
        // 2) SSTable iterators (newest first, but ordering handled by PQ)
        for (String path : sstPaths) {
            // find start offset via binary search in SSTableReader (reuse that class)
            SSTableReader r = new SSTableReader(path);
            long offset = r.findOffset(startKey); // we will add this helper to SSTableReader
            SSTableIterator it = new SSTableIterator(path, offset);
            SourceEntry src = new SourceEntry(it);
            if (src.cur != null) pq.add(src);
        }
    }

    @Override
    public boolean hasNext() {
        while (!pq.isEmpty()) {
            SourceEntry top = pq.peek();
            if (endKey != null && top.cur.getKey().compareTo(endKey) >= 0) return false;
            return true;
        }
        return false;
    }

    @Override
    public Map.Entry<String, String> next() {
        if (!hasNext()) throw new NoSuchElementException();
        SourceEntry smallest = pq.poll();
        Map.Entry<String, String> result = smallest.cur;
        smallest.advance();
        if (smallest.cur != null) pq.add(smallest);
        // skip duplicates from older layers – keep newest value only
        while (!pq.isEmpty() && pq.peek().cur.getKey().equals(result.getKey())) {
            SourceEntry dup = pq.poll();
            dup.advance();
            if (dup.cur != null) pq.add(dup);
        }
        return result;
    }
}
