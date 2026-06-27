import java.util.*;

public class LRUCache<K, V> {
    private final int capacity;
    private final Map<K, Node<K, V>> map;
    private final DoublyLinkedList<K, V> list;

    private static class Node<K, V> {
        K key; V value; Node<K, V> prev; Node<K, V> next;
        Node(K k, V v) { this.key = k; this.value = v; }
    }

    private static class DoublyLinkedList<K, V> {
        Node<K, V> head, tail;
        DoublyLinkedList() { head = new Node<>(null, null); tail = new Node<>(null, null); head.next = tail; tail.prev = head; }
        void moveToFront(Node<K, V> n) { remove(n); addFirst(n); }
        void addFirst(Node<K, V> n) { n.next = head.next; n.prev = head; head.next.prev = n; head.next = n; }
        void remove(Node<K, V> n) { n.prev.next = n.next; n.next.prev = n.prev; }
        Node<K, V> removeLast() { if (tail.prev == head) return null; Node<K, V> last = tail.prev; remove(last); return last; }
    }

    public LRUCache(int capacity) { this.capacity = capacity; this.map = new HashMap<>(); this.list = new DoublyLinkedList<>(); }

    public V get(K key) {
        Node<K, V> n = map.get(key);
        if (n == null) return null;
        list.moveToFront(n);
        return n.value;
    }

    public void put(K key, V value) {
        Node<K, V> n = map.get(key);
        if (n != null) {
            n.value = value;
            list.moveToFront(n);
            return;
        }
        if (map.size() >= capacity) {
            Node<K, V> evicted = list.removeLast();
            if (evicted != null) map.remove(evicted.key);
        }
        Node<K, V> newNode = new Node<>(key, value);
        list.addFirst(newNode);
        map.put(key, newNode);
    }
}
