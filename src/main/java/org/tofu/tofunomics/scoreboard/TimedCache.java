package org.tofu.tofunomics.scoreboard;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 値を一定時間だけ覚えておくキャッシュ。
 * スコアボードやボスバーは毎秒更新されるが、職業や銀行残高を毎秒 DB から読む必要は無いので、
 * 数秒のあいだ同じ値を使い回す。値が変わったと分かったときは {@link #invalidate} で捨てる。
 */
public class TimedCache<K, V> {

    private static final class Entry<V> {
        final V value;
        final long loadedAt;

        Entry(V value, long loadedAt) {
            this.value = value;
            this.loadedAt = loadedAt;
        }
    }

    private final long ttlMillis;
    private final LongSupplier clock;
    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();

    public TimedCache(long ttlMillis) {
        this(ttlMillis, System::currentTimeMillis);
    }

    /** テスト用: 時計を差し替えられる */
    TimedCache(long ttlMillis, LongSupplier clock) {
        this.ttlMillis = ttlMillis;
        this.clock = clock;
    }

    /**
     * 覚えている値が新しければそれを返し、古い（または無い）ときだけ loader で読み直す。
     * loader が null を返したときは覚えない（次回も読み直す）。
     */
    public V get(K key, Supplier<V> loader) {
        long now = clock.getAsLong();
        Entry<V> entry = entries.get(key);
        if (entry != null && isFresh(entry.loadedAt, now, ttlMillis)) {
            return entry.value;
        }
        V value = loader.get();
        if (value == null) {
            entries.remove(key);
        } else {
            entries.put(key, new Entry<>(value, now));
        }
        return value;
    }

    /** 読み込んでから ttl が過ぎていなければ新しい */
    static boolean isFresh(long loadedAt, long now, long ttlMillis) {
        return now - loadedAt < ttlMillis;
    }

    /** 値が変わったと分かったときに呼ぶ。次の get で読み直す */
    public void invalidate(K key) {
        entries.remove(key);
    }

    /** 条件に合うキーをまとめて捨てる（ログアウトした人の掃除用） */
    public void removeIf(Predicate<K> condition) {
        entries.keySet().removeIf(condition);
    }

    public void clear() {
        entries.clear();
    }
}
