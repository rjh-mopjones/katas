package org.kata.quotecache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class QuoteCacheTest {

    // now starts at 10_000 with a 1_000ns TTL, so a quote stored at 10_000 expires at 11_000.
    private final AtomicLong clock = new AtomicLong(10_000);
    private final QuoteCache<String, Double> cache = new QuoteCache<>(1_000, clock::get);

    @Test
    void get_returns_value_before_expiry() {
        cache.put("EURUSD", 1.0850);
        assertEquals(1.0850, cache.get("EURUSD").orElseThrow());
    }

    @Test
    void missing_key_returns_empty() {
        assertTrue(cache.get("GBPUSD").isEmpty());
        assertFalse(cache.containsKey("GBPUSD"));
    }

    @Test
    void expired_entry_is_purged_lazily_on_get() {
        cache.put("EURUSD", 1.0850);
        assertEquals(1, cache.size());

        clock.set(11_000); // age == 1_000 == ttl -> expired
        assertTrue(cache.get("EURUSD").isEmpty());
        assertEquals(0, cache.size(), "get() must lazily purge the expired entry");
    }

    @Test
    void size_reflects_only_live_entries_and_purges_stale_ones() {
        cache.put("EURUSD", 1.0850);
        clock.set(10_500);
        cache.put("GBPUSD", 1.2650); // stored later, still fresh at 11_500

        clock.set(11_100); // EURUSD (age 1_100) expired; GBPUSD (age 600) live
        assertEquals(1, cache.size());
        assertTrue(cache.get("EURUSD").isEmpty());
        assertTrue(cache.get("GBPUSD").isPresent());
    }

    @Test
    void exact_ttl_boundary_is_expired() {
        cache.put("EURUSD", 1.0850);
        clock.set(10_999); // age = 999 < 1_000 -> still live
        assertTrue(cache.get("EURUSD").isPresent());

        clock.set(11_000); // age = 1_000 == ttl -> expired
        assertTrue(cache.get("EURUSD").isEmpty());
    }

    @Test
    void overwrite_resets_the_ttl() {
        cache.put("EURUSD", 1.0850);
        clock.set(10_900); // age 900, about to expire at 11_000
        cache.put("EURUSD", 1.0860); // overwrite resets storedAt to 10_900

        clock.set(11_000); // age since original write is 1_000, but since overwrite only 100
        assertEquals(1.0860, cache.get("EURUSD").orElseThrow(),
                "overwrite must reset the TTL clock, keeping the entry alive past the original deadline");
    }

    @Test
    void contains_key_reflects_liveness() {
        cache.put("EURUSD", 1.0850);
        assertTrue(cache.containsKey("EURUSD"));

        clock.set(11_000);
        assertFalse(cache.containsKey("EURUSD"));
    }

    @Test
    void non_positive_ttl_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new QuoteCache<String, Double>(0, clock::get));
        assertThrows(IllegalArgumentException.class, () -> new QuoteCache<String, Double>(-5, clock::get));
    }

    // ---- Concurrent stress test ----

    /**
     * N virtual threads each put a distinct key with a large TTL (so nothing expires mid-test),
     * gated to start simultaneously for maximum contention. Afterwards every key must be present
     * and {@code size()} must equal N exactly — conservation of writes under concurrent access
     * to a single {@link ConcurrentHashMap}-backed cache, with no external locking.
     */
    @Test
    void concurrent_puts_of_distinct_keys_are_all_conserved() throws InterruptedException {
        final int n = 200;
        var bigTtlCache = new QuoteCache<Integer, String>(1_000_000_000L, clock::get);

        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(n);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < n; i++) {
                final int key = i;
                exec.submit(() -> {
                    try {
                        gate.await();
                        bigTtlCache.put(key, "quote-" + key);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS), "threads did not complete within 5 seconds");
        }

        assertEquals(n, bigTtlCache.size());
        for (int i = 0; i < n; i++) {
            assertEquals("quote-" + i, bigTtlCache.get(i).orElseThrow(),
                    "key " + i + " was lost under concurrent put");
        }
    }
}
