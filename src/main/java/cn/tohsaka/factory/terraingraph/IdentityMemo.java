package cn.tohsaka.factory.terraingraph;

import java.util.IdentityHashMap;

/** Holds transformed nodes for a single traversal, without changing structural equality. */
public final class IdentityMemo<K, V> {
    private final IdentityHashMap<K, V> values = new IdentityHashMap<>();
    private final int limit;

    public IdentityMemo(int limit) {
        this.limit = limit;
    }

    public V get(K key) {
        return values.get(key);
    }

    public void remember(K key, V value) {
        if (value != null && values.size() < limit) {
            values.put(key, value);
        }
    }

    public int size() {
        return values.size();
    }
}
