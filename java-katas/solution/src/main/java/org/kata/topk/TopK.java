package org.kata.topk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The K highest-scoring keys over an unbounded stream of observations — the "biggest movers" panel
 * on a live feed (most-traded selections, top gainers, trending symbols) recomputed on every tick.
 *
 * <p>Every observation is {@code add(key, weight)}: the key's cumulative score changes by
 * {@code weight} (which may be negative — a key can fall as well as rise). {@link #top()} must
 * always answer with the current top {@code k}, ranked by score descending, ties broken by key
 * ascending so two callers reading the same state see the same order.
 *
 * <h2>Data structure</h2>
 * A {@link HashMap}{@code <String, Long>} holds every key's current score (the source of truth), and
 * a {@link TreeSet}{@code <Entry>} — ordered by score descending, then key ascending — holds the same
 * keys ranked. On {@code add}, the key's old {@code Entry} is removed from the set, the map is
 * updated, and the new {@code Entry} is re-inserted; both are O(log n) tree operations, so a stream
 * of N updates over a distinct-key population of n costs O(N log n) total, not O(N·n). {@link #top()}
 * is a single O(k) walk of the set's head.
 *
 * <p>The set can't simply be re-sorted in place: {@link Entry} is an immutable record, so "the score
 * changed" means "a different tree node" — remove-then-reinsert is the only correct move, and it must
 * use the <em>old</em> score to find the old node (looking it up by key alone, after the map is
 * already updated, would miss it).
 *
 * <h2>Why not a bounded heap</h2>
 * A fixed-size-K min-heap of the current leaders is the standard "top-K of a stream" answer, and it
 * beats this design on memory when the key space is huge and K is small: it holds only K entries
 * instead of one per distinct key. It falls down here because the requirement is not "insert new
 * values and evict the smallest" but <b>rescore an arbitrary existing key</b> — a key already in the
 * top K, or one currently outside it, can move in either direction on every tick. A plain
 * {@code PriorityQueue} has no efficient "find and re-rank this element" operation; making one work
 * needs a heap augmented with a key→array-index map kept in sync across every swap (a poor-man's
 * indexed/addressable heap), which is more bookkeeping than the {@code TreeSet} for the same
 * O(log K) bound, and it can't tell you {@link #scoreOf(String)} for a key that has fallen out of the
 * top K, whereas the map here always can. The bounded heap wins when scores are append-only extremes
 * (a running max, not an updatable score) or the key population is orders of magnitude larger than
 * memory allows for exact tracking (see the Count-Min Sketch extension below).
 *
 * <h2>Possible extensions</h2>
 * <ul>
 *   <li><b>Bounded top-K heap</b> — when the key space is small relative to K and only-ever-grows-then-
 *       occasionally-shrinks-out-of-view semantics apply, an indexed min-heap of size K plus a
 *       key→heap-index map gets updates to O(log K) instead of O(log n), at the cost of exact
 *       {@code scoreOf} for keys outside the top K.</li>
 *   <li><b>Count-Min Sketch + heap</b> — for a massive or unbounded key space (millions of distinct
 *       symbols) where per-key exact tracking is too much memory, approximate every score with a
 *       Count-Min Sketch (sublinear memory, no per-key entry) and maintain a small heap of
 *       sketch-estimated leaders; trades exactness (bounded over-counting) for memory.</li>
 *   <li><b>Decay</b> — an exponentially-decaying score (recent activity outweighs old) instead of a
 *       plain cumulative sum, so "biggest movers" reflects momentum, not all-time volume.</li>
 * </ul>
 */
public final class TopK {

    private static final Comparator<Entry> RANKING =
            Comparator.comparingLong(Entry::score).reversed()
                    .thenComparing(Entry::key);

    private final int k;
    private final Map<String, Long> scores = new HashMap<>();
    private final TreeSet<Entry> ranked = new TreeSet<>(RANKING);

    public TopK(int k) {
        if (k < 0) {
            throw new IllegalArgumentException("k must be non-negative: " + k);
        }
        this.k = k;
    }

    /** Add {@code weight} (possibly negative) to {@code key}'s cumulative score. */
    public void add(String key, long weight) {
        long oldScore = scores.getOrDefault(key, 0L);
        long newScore = oldScore + weight;
        if (scores.containsKey(key)) {
            ranked.remove(new Entry(key, oldScore));
        }
        scores.put(key, newScore);
        ranked.add(new Entry(key, newScore));
    }

    /** Shorthand for {@code add(key, 1)}. */
    public void increment(String key) {
        add(key, 1);
    }

    /**
     * The current top {@code k} entries, highest score first, ties broken by key ascending. Fewer
     * than {@code k} entries are returned when fewer distinct keys have been observed; empty when
     * {@code k == 0} or nothing has been added.
     */
    public List<Entry> top() {
        List<Entry> result = new ArrayList<>(Math.min(k, ranked.size()));
        for (Entry entry : ranked) {
            if (result.size() == k) {
                break;
            }
            result.add(entry);
        }
        return result;
    }

    /** Current cumulative score of {@code key}, or {@code 0} if it has never been observed. */
    public long scoreOf(String key) {
        return scores.getOrDefault(key, 0L);
    }
}
