package org.kata.idempotency;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Wraps arbitrary processing so a message identified by an idempotency key is processed
 * <em>at most once</em>, even under concurrent duplicate delivery.
 *
 * <h2>The problem: at-least-once delivery + duplicate actions</h2>
 * Message brokers (Kafka, SQS, RabbitMQ) guarantee <em>at-least-once delivery</em> — a message
 * may be delivered more than once during rebalances, retries, or consumer restarts. Without
 * idempotency controls, processing a "charge user $50" message twice would result in double
 * billing. The solution is the <em>idempotent consumer</em> pattern:
 * <ol>
 *   <li>Every message carries a unique idempotency key (e.g., a UUID set by the producer).</li>
 *   <li>The consumer checks whether it has already processed a key before acting.</li>
 *   <li>If already processed, it returns the cached result without running the action again.</li>
 * </ol>
 *
 * <h2>Implementation: {@link ConcurrentHashMap#computeIfAbsent}</h2>
 * {@code computeIfAbsent(key, mappingFunction)} atomically checks for the key, and if absent,
 * evaluates the mapping function and inserts the result — all as a single operation from the map's
 * perspective. Even when multiple threads call {@code process} for the same key simultaneously,
 * {@code computeIfAbsent} guarantees:
 * <ul>
 *   <li>The mapping function runs <em>at most once</em> per key. Competing threads block (briefly,
 *       on the internal bin lock) until the first invocation completes and the result is stored.
 *       Subsequent callers see the cached result immediately.</li>
 *   <li>All concurrent callers for the same key receive the <em>same result object</em>.</li>
 * </ul>
 * This is the crux of exactly-once-per-key semantics in-process — worth explaining clearly in
 * an interview, because the naive approach (get, null-check, put) has a TOCTOU race window.
 *
 * <h2>Real-world context</h2>
 * <ul>
 *   <li><b>Kafka consumer dedup</b> — store processed offsets or message keys in Redis/DB before
 *       committing the offset; on replay check the store before re-executing.</li>
 *   <li><b>Payment idempotency</b> (primer Q116, Q305) — Stripe's API requires clients to send
 *       an {@code Idempotency-Key} header; the server caches the response for that key so
 *       retries on network failure return the original outcome rather than charging twice.</li>
 *   <li><b>Database upsert</b> — INSERT ... ON CONFLICT DO NOTHING with a unique constraint on
 *       the idempotency key is the persistence-layer equivalent.</li>
 * </ul>
 *
 * <h2>TTL / eviction — production concern</h2>
 * This in-memory implementation has unbounded key growth: keys accumulate forever. In production
 * you would replace the plain {@code ConcurrentHashMap} with a bounded or TTL-expiring store:
 * <ul>
 *   <li>Caffeine / Guava's {@code CacheBuilder} — bounded LRU with TTL, pure in-process.</li>
 *   <li>Redis with {@code SET NX EX} — distributed, survives restarts, TTL is native.</li>
 *   <li>A relational table with a unique index on the key and a scheduled cleanup job.</li>
 * </ul>
 * For the kata the unbounded map is correct and sufficient.
 *
 * <h2>Thread safety of the action result</h2>
 * The stored result is the value returned by {@code action.get()}. If the result type is mutable
 * and callers mutate the returned object, they are mutating the cached copy. For safety, prefer
 * immutable result types (records, unmodifiable collections). This class does not defensively
 * copy results — that would be impossible in a generic context and is the caller's responsibility.
 */
public class IdempotentProcessor {

    // Keys map to their cached result. ConcurrentHashMap: thread-safe structural modifications
    // and the critical computeIfAbsent atomicity guarantee. Using Object as the value type
    // because we store results of different T across multiple process() calls. The cast in
    // process() is safe because we always store the result of the caller-provided Supplier<T>.
    private final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();

    /**
     * Process a message identified by {@code idempotencyKey}, running {@code action} at most once.
     *
     * <ul>
     *   <li>If this key has never been seen, {@code action.get()} is called, its result is
     *       cached, and the result is returned.</li>
     *   <li>If this key has already been processed, the cached result is returned immediately
     *       without calling {@code action} again — even if many threads present the same key
     *       concurrently.</li>
     * </ul>
     *
     * <p><b>Exactly-once guarantee scope:</b> this guarantee is limited to a single JVM. For
     * distributed exactly-once, the cache must be a shared external store (Redis, DB) and the
     * write must be made durable <em>before</em> acknowledging the message to the broker.
     *
     * @param idempotencyKey globally unique identifier for this message; must not be null.
     * @param action         the operation to execute on first touch; must not be null. Its return
     *                       value is cached. The action must not return null — null cannot be
     *                       stored in ConcurrentHashMap and would signal an absent key on the
     *                       next lookup, causing the action to run again.
     * @param <T>            the result type.
     * @return the result of {@code action} (first call) or the cached result (subsequent calls).
     */
    @SuppressWarnings("unchecked")
    public <T> T process(String idempotencyKey, Supplier<T> action) {
        if (idempotencyKey == null) throw new IllegalArgumentException("idempotencyKey must not be null");
        if (action == null)         throw new IllegalArgumentException("action must not be null");

        // computeIfAbsent: the mapping function runs at most once per absent key, even under
        // concurrent contention. This is the contract Java's ConcurrentHashMap makes — the bin
        // lock prevents two threads from executing the mapping function for the same key
        // simultaneously. Any thread that arrives while the mapping is in progress blocks until
        // the result is stored, then reads from the map.
        //
        // Why not: if (cache.containsKey(key)) return cache.get(key); else cache.put(key, ...)?
        // That is a TOCTOU (Time Of Check / Time Of Use) race: between the containsKey check and
        // the put, another thread can execute the same sequence and both end up running the action.
        Object result = cache.computeIfAbsent(idempotencyKey, k -> {
            // This lambda executes at most once per key. The cast from T to Object is safe
            // because we recover it with the symmetric (T) cast in the return statement.
            T value = action.get();
            if (value == null) {
                throw new IllegalStateException(
                    "action must not return null — ConcurrentHashMap cannot store null values, " +
                    "and null would be misinterpreted as an absent key on the next lookup");
            }
            return value;
        });

        // Safe cast: we only store values produced by Supplier<T>, and the generic type T is
        // fixed for the lifetime of a single process() call. The caller controls the Supplier
        // and knows its return type.
        return (T) result;
    }

    /**
     * Returns whether a given idempotency key has already been processed.
     *
     * <p>This is a snapshot check — the key may be inserted by another thread immediately after
     * this method returns {@code false}. Use it for monitoring / health-check endpoints, not for
     * conditional logic (use {@link #process} for that — it is atomically correct).
     *
     * @param idempotencyKey the key to query.
     * @return {@code true} if the key is present in the cache.
     */
    public boolean isProcessed(String idempotencyKey) {
        return cache.containsKey(idempotencyKey);
    }
}
