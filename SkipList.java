import java.util.*;

public class SkipList<K extends Comparable<K>, V> {
    private static final int MAX_LEVEL = 16;
    final Node<K, V> head = new Node<>(null, null, MAX_LEVEL);
    private int level = 1;
    private int size = 0;

    static class Node<K, V> {
        final K key;
        V value;
        final Node<K, V>[] forward;
        @SuppressWarnings("unchecked")
        Node(K key, V value, int level) {
            this.key = key;
            this.value = value;
            this.forward = (Node<K, V>[]) new Node[level];
        }
    }

    private int randomLevel() {
        int lvl = 1;
        while (Math.random() < 0.5 && lvl < MAX_LEVEL) {
            lvl++;
        }
        return lvl;
    }

    public void put(K key, V value) {
        @SuppressWarnings("unchecked")
        Node<K, V>[] update = new Node[MAX_LEVEL];
        Node<K, V> x = head;
        for (int i = level - 1; i >= 0; i--) {
            while (x.forward[i] != null && x.forward[i].key.compareTo(key) < 0) {
                x = x.forward[i];
            }
            update[i] = x;
        }
        x = x.forward[0];
        if (x != null && x.key.equals(key)) {
            x.value = value; // replace existing
            return;
        }
        int lvl = randomLevel();
        if (lvl > level) {
            for (int i = level; i < lvl; i++) update[i] = head;
            level = lvl;
        }
        Node<K, V> newNode = new Node<>(key, value, lvl);
        for (int i = 0; i < lvl; i++) {
            newNode.forward[i] = update[i].forward[i];
            update[i].forward[i] = newNode;
        }
        size++;
    }

    public V get(K key) {
        Node<K, V> x = head;
        for (int i = level - 1; i >= 0; i--) {
            while (x.forward[i] != null && x.forward[i].key.compareTo(key) < 0) {
                x = x.forward[i];
            }
        }
        x = x.forward[0];
        if (x != null && x.key.equals(key)) return x.value;
        return null;
    }

    public int size() { return size; }

    public Iterator<Map.Entry<K, V>> iteratorFrom(K start) {
        Node<K, V> x = head;
        for (int i = level - 1; i >= 0; i--) {
            while (x.forward[i] != null && x.forward[i].key.compareTo(start) < 0) {
                x = x.forward[i];
            }
        }
        // now x.forward[0] is the first node >= start (or null)
        final Node<K, V> first = (x.forward[0] != null) ? x.forward[0] : null;
        return new Iterator<>() {
            Node<K, V> cur = first;
            @Override public boolean hasNext() { return cur != null; }
            @Override public Map.Entry<K, V> next() {
                if (cur == null) throw new NoSuchElementException();
                Map.Entry<K, V> e = new AbstractMap.SimpleImmutableEntry<>(cur.key, cur.value);
                cur = cur.forward[0];
                return e;
            }
        };
    }
}
