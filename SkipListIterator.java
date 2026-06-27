import java.util.*;

public class SkipListIterator implements Iterator<Map.Entry<String, String>> {
    private final SkipList<String, String> skiplist;
    private SkipList.Node<String, String> current;

    public SkipListIterator(SkipList<String, String> skiplist) {
        this.skiplist = skiplist;
        // start at the first real node
        this.current = skiplist.head.forward[0];
    }

    @Override
    public boolean hasNext() { return current != null; }

    @Override
    public Map.Entry<String, String> next() {
        if (!hasNext()) throw new NoSuchElementException();
        Map.Entry<String, String> entry = new AbstractMap.SimpleImmutableEntry<>(current.key, current.value);
        current = current.forward[0];
        return entry;
    }
}
