package org.kata.pipeline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * A small fluent transformation pipeline over an in-memory batch of elements — the kata vehicle for
 * <b>bounded wildcards</b> (PECS: Producer-Extends, Consumer-Super) and what <b>type erasure</b> does
 * and doesn't let you do with a type parameter at runtime.
 *
 * <p>{@code Pipeline<T>} holds a {@link List} of {@code T} and offers four operations that only differ
 * from the "obvious" unbounded-generic signature in their wildcard bounds — the wildcards are the whole
 * point, not decoration. Every method here would still compile with the naive signature (say,
 * {@code addAll(Collection<T>)}); the wildcard version is what lets client code build against a real
 * type <em>hierarchy</em> (e.g. loading a {@code Pipeline<Number>} from a {@code List<Integer>})
 * instead of forcing exact-type matches everywhere.
 *
 * <h2>Producer-extends: {@link #addAll} and the static {@link #copy}</h2>
 * A parameter that only ever gives you elements — you only ever <em>read</em> from it — is a
 * <b>producer</b>, and a producer should be declared {@code Collection<? extends T>}. That lets
 * {@code addAll} accept a {@code List<Integer>} into a {@code Pipeline<Number>}: every {@code Integer}
 * <em>is</em> a {@code Number}, so reading one out and adding it to the internal {@code List<T>} is
 * always sound. The same idea appears twice in {@link #copy(List, List)}: {@code src} is
 * {@code List<? extends T>} because we only read from it. Note what {@code ? extends T} costs you in
 * return: you cannot call {@code src.add(...)} — the compiler cannot prove <em>which</em> subtype of
 * {@code T} the list actually holds, so writing to it is rejected at compile time. That rejection is
 * the mechanism, not a limitation to work around.
 *
 * <h2>Consumer-super: {@link #drainTo} and the other half of {@link #copy}</h2>
 * A parameter you only ever <em>write into</em> — you hand it elements, you never read from it — is a
 * <b>consumer</b>, and a consumer should be declared {@code Collection<? super T>}. That lets
 * {@code drainTo} empty a {@code Pipeline<Number>} into a {@code List<Object>}: every {@code Number} is
 * an {@code Object}, so widening on the way in is always sound, and it lets one sink collection serve
 * several differently-typed pipelines. In {@link #copy(List, List)}, {@code dst} is
 * {@code List<? super T>} for the same reason. Symmetrically, {@code ? super T} forbids reading a
 * meaningfully-typed element back out — {@code dst.get(0)} only gives you {@code Object} — because the
 * compiler cannot prove the actual element type is anything more specific than {@code T}'s upper bound.
 *
 * <h2>Invariant middle: {@link #map}</h2>
 * {@code map} takes {@code Function<? super T, ? extends R>} — a producer/consumer pair on the
 * <em>function</em> itself, not on a collection. The function consumes {@code T} (so it must accept at
 * least {@code T}, hence {@code ? super T}) and produces {@code R} (so it must yield at most {@code R},
 * hence {@code ? extends R}) — this is the same PECS reasoning applied to {@link Function}, and it is
 * exactly the bound the JDK puts on {@link java.util.stream.Stream#map}. The plain
 * {@code List<T> items} field itself stays invariant ({@code List<T>}, no wildcard): it is read AND
 * written internally, so neither variance direction alone would be sound.
 *
 * <h2>Why a static factory instead of {@code new Pipeline<>()}}</h2>
 * {@link #create()} exists so the type argument can be inferred (or supplied explicitly as
 * {@code Pipeline.<Integer>create()}) from the call site without repeating {@code <T>} on both sides of
 * an assignment, and so the no-arg constructor can stay {@code private} — the only entry points are
 * {@code create()} and {@code map}, both of which know the element type of the {@link List} they hand
 * to the private constructor.
 *
 * <h2>Type erasure consequences</h2>
 * At runtime a {@code Pipeline<Integer>} and a {@code Pipeline<String>} are the same class — the
 * compiler erases {@code T} to its bound ({@code Object} here) after checking the wildcards above, and
 * generates the unchecked casts needed to make that safe. Three concrete consequences show up if you
 * try to "improve" this class:
 * <ul>
 *   <li><b>{@code new T[n]} does not compile.</b> The JVM needs a reified component type to allocate an
 *       array, and erasure has thrown {@code T} away by the time the array would be created. The
 *       standard workaround — used elsewhere in this repo (e.g. a hand-rolled hash map or ring buffer)
 *       — is to back the structure with {@code Object[]} and cast on read, accepting an unchecked
 *       warning at that one boundary. {@code Pipeline} sidesteps the problem entirely by backing itself
 *       with a {@link List}, which is the idiomatic real-world choice precisely because
 *       {@code ArrayList} already hides its own {@code Object[]} behind a type-safe API.</li>
 *   <li><b>Generic varargs risk heap pollution.</b> A hypothetical
 *       {@code static <T> Pipeline<T> of(T... items)} factory would compile with an "unchecked
 *       generic array creation" warning, because {@code items} is really a single shared
 *       {@code Object[]} at runtime — nothing stops another erased-generic method from stashing a
 *       different type into that same array before it is read. {@code @SafeVarargs} is a promise to
 *       the compiler ("this method does not do that"), not a fix; it only suppresses the warning once
 *       the author has manually verified the method is safe.</li>
 *   <li><b>Unchecked casts are how the compiler discharges wildcard reasoning at runtime.</b> Every
 *       {@code ? extends}/{@code ? super} bound above is purely a compile-time proof; erasure means the
 *       bytecode for {@code addAll}, {@code map}, and {@code copy} is identical regardless of which
 *       concrete types were used, with an implicit cast to {@code T} inserted wherever the erased code
 *       reads a value the source guaranteed was assignable.</li>
 * </ul>
 *
 * <h2>Possible extensions</h2>
 * <ul>
 *   <li><b>Recursive generic bounds</b> — a {@code sorted()} operation would need
 *       {@code <T extends Comparable<? super T>>} on the class or method: {@code T} must be comparable
 *       to itself or one of its own supertypes, the same bound {@link java.util.Collections#max} uses.</li>
 *   <li><b>{@code @SafeVarargs} factory</b> — add {@code static <T> Pipeline<T> of(T... items)} once the
 *       varargs body is verified never to write into the shared array, so callers can write
 *       {@code Pipeline.of(1, 2, 3)} instead of {@code addAll(List.of(1, 2, 3))}.</li>
 *   <li><b>Lazy/streaming pipeline</b> — replace the eager {@code List<T>} with a
 *       {@link java.util.stream.Stream}-backed implementation so {@code map} composes functions instead
 *       of materialising an intermediate list at every stage.</li>
 * </ul>
 */
public final class Pipeline<T> {

    private final List<T> items;

    private Pipeline(List<T> items) {
        this.items = items;
    }

    /** Start an empty pipeline of element type {@code T}. */
    public static <T> Pipeline<T> create() {
        return new Pipeline<>(new ArrayList<>());
    }

    /**
     * Append every element of {@code items} to this pipeline and return {@code this} for chaining.
     *
     * <p>{@code items} is a <b>producer</b> — this method only reads from it — so it is declared
     * {@code Collection<? extends T>}: a {@code Collection<Integer>} can be added into a
     * {@code Pipeline<Number>}.
     */
    public Pipeline<T> addAll(Collection<? extends T> items) {
        this.items.addAll(items);
        return this;
    }

    /**
     * Apply {@code fn} to every element and return a new pipeline of the mapped type {@code R}; this
     * pipeline is left untouched.
     *
     * <p>{@code fn} both consumes {@code T} ({@code ? super T}) and produces {@code R}
     * ({@code ? extends R}) — the PECS reasoning applied to a {@link Function} instead of a
     * {@link Collection}.
     */
    public <R> Pipeline<R> map(Function<? super T, ? extends R> fn) {
        List<R> mapped = new ArrayList<>(items.size());
        for (T item : items) {
            mapped.add(fn.apply(item));
        }
        return new Pipeline<>(mapped);
    }

    /**
     * Move every element out of this pipeline into {@code sink}, leaving this pipeline empty.
     *
     * <p>{@code sink} is a <b>consumer</b> — this method only writes into it — so it is declared
     * {@code Collection<? super T>}: a {@code Pipeline<Number>} can drain into a {@code List<Object>}.
     */
    public void drainTo(Collection<? super T> sink) {
        sink.addAll(items);
        items.clear();
    }

    /** A defensive-copy snapshot of the elements currently in this pipeline, in order. */
    public List<T> toList() {
        return new ArrayList<>(items);
    }

    /**
     * Append every element of {@code src} onto {@code dst} — a standalone demonstration of the same
     * PECS pair as instance methods above, mirroring {@link java.util.Collections#copy}: {@code src} is
     * a producer ({@code ? extends T}), {@code dst} is a consumer ({@code ? super T}).
     */
    public static <T> void copy(List<? super T> dst, List<? extends T> src) {
        for (T item : src) {
            dst.add(item);
        }
    }
}
