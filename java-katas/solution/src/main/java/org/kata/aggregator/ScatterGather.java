package org.kata.aggregator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Scatter-gather over a list of async tasks.
 *
 * <h2>The pattern</h2>
 * Scatter-gather (also called fan-out / fan-in) distributes a query to N independent sources
 * concurrently (the scatter), then aggregates all their results into a single response (the
 * gather). Real-world uses:
 * <ul>
 *   <li><b>Federated search</b> — fan out to multiple search shards or providers and merge
 *       ranked results.</li>
 *   <li><b>Price aggregators</b> — query multiple suppliers in parallel and return the
 *       cheapest.</li>
 *   <li><b>Microservice fan-out</b> — a gateway enriches a response by calling several
 *       downstream services concurrently instead of sequentially (reduces latency by the
 *       overlap in per-task execution times).</li>
 * </ul>
 *
 * <h2>{@link CompletableFuture#allOf} vs {@link CompletableFuture#anyOf}</h2>
 * <ul>
 *   <li>{@code allOf} — returns a {@code CompletableFuture<Void>} that completes when ALL
 *       supplied futures complete. If any one fails, the returned future fails immediately.
 *       Use when you need every result (e.g., aggregating enrichments that are all required).</li>
 *   <li>{@code anyOf} — completes as soon as the FIRST future completes (success or failure).
 *       Use for hedged requests (send to redundant replicas; take the fastest reply).</li>
 * </ul>
 * Note that {@code allOf} returns {@code Void}, not the results. You must collect results from
 * the original individual futures after the barrier completes — a common interview stumbling
 * block.
 *
 * <h2>Exception handling</h2>
 * {@code gatherAll} propagates the first failure via the returned future's exceptional completion.
 * Callers use {@code .join()} (which rethrows as {@link java.util.concurrent.CompletionException})
 * or {@code .get()} (throws {@link java.util.concurrent.ExecutionException}).
 *
 * <p>{@code gatherAllWithTimeout} wraps each individual future with {@code orTimeout}: if a
 * task exceeds the deadline its future completes exceptionally. We then discard any futures that
 * completed exceptionally (timed out or failed), keeping only the fast/successful results. This
 * is the partial-result-on-timeout pattern used in search aggregators: better to return fewer
 * results quickly than to wait for slow shards and degrade the whole request.
 *
 * <h2>Executor choice</h2>
 * The default {@link Executors#newVirtualThreadPerTaskExecutor()} creates a new virtual thread
 * per task. Virtual threads are cheap (JEP 425/444) so we don't need to pre-size a pool.
 * For CPU-intensive tasks use {@link java.util.concurrent.ForkJoinPool#commonPool()} or a
 * bounded platform-thread pool to avoid over-provisioning.
 *
 * <h2>Caller-provided tasks vs pre-built futures</h2>
 * Accepting {@link Supplier}{@code <T>} (rather than pre-built {@code CompletableFuture<T>})
 * keeps scatter lazy: tasks start only when {@code gatherAll} is called, and on the executor
 * of our choice. Pre-built futures would start immediately on whatever thread the caller used,
 * making the executor parameter meaningless.
 */
public class ScatterGather {

    private final Executor executor;

    /**
     * Constructs a {@code ScatterGather} using a virtual-thread executor.
     * Virtual threads (Java 21+) are the right default: they are cheap to create, block without
     * pinning platform threads, and require no pool sizing.
     */
    public ScatterGather() {
        // newVirtualThreadPerTaskExecutor is not an AutoCloseable in all usages; we keep a
        // reference here. If the ScatterGather is a long-lived bean you may want to close it
        // on application shutdown, but for a kata the GC handles it.
        this(Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * @param executor the executor on which to run each scattered task. Inject a
     *                 {@link java.util.concurrent.ExecutorService} for tests that want to
     *                 control thread lifecycle, or the virtual-thread executor for production.
     */
    public ScatterGather(Executor executor) {
        if (executor == null) throw new IllegalArgumentException("executor must not be null");
        this.executor = executor;
    }

    /**
     * Scatter tasks concurrently and gather ALL results.
     *
     * <p>Each task in {@code tasks} is submitted to the executor independently. All tasks run
     * in parallel. The returned future completes only when every task completes:
     * <ul>
     *   <li>If all succeed, the future holds the list of results in the same order as
     *       {@code tasks}.</li>
     *   <li>If any task fails (throws), the returned future completes exceptionally with that
     *       exception (wrapped in {@link java.util.concurrent.CompletionException}). Other tasks
     *       continue running — we do not cancel them on first failure, a deliberate trade-off
     *       (cancellation is possible via {@link CompletableFuture#cancel}, but adds complexity
     *       and is rarely needed for short-lived fan-outs).</li>
     * </ul>
     *
     * @param tasks list of suppliers; each provides one result. Must not be null or contain null.
     * @param <T>   the result type.
     * @return a future that completes with the ordered list of all results when every task
     *         succeeds, or completes exceptionally on the first failure.
     */
    public <T> CompletableFuture<List<T>> gatherAll(List<Supplier<T>> tasks) {
        if (tasks == null) throw new IllegalArgumentException("tasks must not be null");

        // Scatter: start every task concurrently and keep a reference to each future.
        // We must keep the individual futures because allOf returns Void — the results live
        // in the individual futures, not in the barrier future itself.
        List<CompletableFuture<T>> futures = new ArrayList<>(tasks.size());
        for (Supplier<T> task : tasks) {
            // supplyAsync submits the task to our executor and wraps the result (or exception)
            // into the returned CompletableFuture. If the task throws, the future completes
            // exceptionally rather than propagating to the caller thread.
            futures.add(CompletableFuture.supplyAsync(task, executor));
        }

        // Barrier: completes when ALL futures complete (success or failure).
        // allOf returns CompletableFuture<Void> — no results here, just the completion signal.
        CompletableFuture<Void> barrier = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));

        // Gather: once the barrier fires, pull results from each individual future.
        // At this point every future is guaranteed to be done (allOf just completed), so
        // future.join() cannot block and will not throw InterruptedException. If any future
        // completed exceptionally, join() rethrows it here, completing the gathered future
        // exceptionally in turn — which is the desired "fail fast" behaviour for gatherAll.
        return barrier.thenApply(ignored -> {
            List<T> results = new ArrayList<>(futures.size());
            for (CompletableFuture<T> f : futures) {
                results.add(f.join());   // join is safe: barrier ensures f is done
            }
            return results;
        });
    }

    /**
     * Scatter tasks concurrently and gather the results that finish before the deadline.
     *
     * <p>Each task is given at most {@code timeout} time to complete. Tasks that finish within
     * the deadline contribute their result to the output list. Tasks that time out or fail are
     * silently dropped — the caller receives a partial result.
     *
     * <p>This is the right trade-off for search fan-out: a slow shard should not hold up the
     * entire response. The caller decides whether a partial result is sufficient or whether to
     * fall back to a default.
     *
     * <p><b>Implementation note — {@code orTimeout} vs {@code completeOnTimeout}.</b>
     * {@code orTimeout} completes the future exceptionally with a {@link java.util.concurrent.TimeoutException}
     * when the deadline elapses. {@code completeOnTimeout(default, ...)}, by contrast, completes
     * it normally with a default value. We use {@code orTimeout} here and then test for
     * exceptional completion, which avoids having to pick a "sentinel" default value that is
     * meaningful across all callers.
     *
     * @param tasks   list of suppliers; each provides one result.
     * @param timeout maximum time each individual task may run before it is dropped.
     * @param <T>     the result type.
     * @return a future that completes with whatever results arrived before the deadline.
     *         The returned list may be empty if no tasks finished in time.
     */
    public <T> CompletableFuture<List<T>> gatherAllWithTimeout(
            List<Supplier<T>> tasks, Duration timeout) {
        if (tasks == null) throw new IllegalArgumentException("tasks must not be null");
        if (timeout == null || timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("timeout must be positive");

        long timeoutNanos = timeout.toNanos();

        // Scatter: start all tasks and apply a per-future timeout.
        // orTimeout wraps each future with a deadline — if the task doesn't complete in time,
        // the future completes exceptionally. Deadlines are applied independently per task so
        // they all share the same wall-clock window (not stacked sequentially).
        List<CompletableFuture<Optional<T>>> wrapped = new ArrayList<>(tasks.size());
        for (Supplier<T> task : tasks) {
            CompletableFuture<T> raw = CompletableFuture.supplyAsync(task, executor);
            // .orTimeout makes the future fail on timeout.
            // .handle converts both normal and exceptional completion into an Optional:
            //   - success → Optional.of(value)
            //   - failure/timeout → Optional.empty()
            // This removes the exceptional state so allOf won't itself fail.
            CompletableFuture<Optional<T>> safe = raw
                    .orTimeout(timeoutNanos, TimeUnit.NANOSECONDS)
                    .handle((value, ex) -> ex == null ? Optional.of(value) : Optional.empty());
            wrapped.add(safe);
        }

        // Barrier: wait for all futures (they can no longer fail — handle absorbed exceptions).
        CompletableFuture<Void> barrier = CompletableFuture.allOf(
                wrapped.toArray(new CompletableFuture[0]));

        // Gather: collect only the futures that produced a value.
        return barrier.thenApply(ignored -> {
            List<T> results = new ArrayList<>(wrapped.size());
            for (CompletableFuture<Optional<T>> f : wrapped) {
                // join() is safe here — barrier guarantees every future is done.
                // unwrap only Optionals that are present (i.e., task succeeded within timeout).
                f.join().ifPresent(results::add);
            }
            return results;
        });
    }
}
