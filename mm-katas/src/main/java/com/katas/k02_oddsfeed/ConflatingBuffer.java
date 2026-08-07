package com.katas.k02_oddsfeed;

import java.util.Optional;

/**
 * A backpressure buffer between a fast producer and a slower consumer that conflates by
 * {@code selection}: only the latest (highest-{@code seq}) update per selection is retained, so
 * memory is bounded by the number of distinct selections in flight, not by the number of offers.
 *
 * <p>Producer ({@link #offer}) and consumer ({@link #poll}) run on different threads; the buffer
 * must be safe under concurrent access and must never lose the latest update for a selection.
 *
 * <p>TODO: implement every method. They all throw until you do.
 */
public final class ConflatingBuffer {

    /** Create an empty buffer. */
    public ConflatingBuffer() {
        // TODO: keep whatever state you need.
    }

    /**
     * Producer side: offer an update. Conflate by {@code selection}, keeping the highest-{@code seq}
     * update per selection; an offer whose {@code seq} is not newer than the currently-buffered one
     * for that selection is ignored.
     */
    public void offer(OddsUpdate update) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Consumer side: take the next pending selection's latest update, or empty if none is buffered. */
    public Optional<OddsUpdate> poll() {
        throw new UnsupportedOperationException("TODO");
    }

    /** Number of distinct selections currently buffered (the buffer's memory is bounded by this). */
    public int pending() {
        throw new UnsupportedOperationException("TODO");
    }
}
