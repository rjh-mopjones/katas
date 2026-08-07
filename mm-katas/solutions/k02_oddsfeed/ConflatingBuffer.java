package com.katas.k02_oddsfeed;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Worked reference — Stage 4. See NOTES.md for the design.
 *
 * <p>Design: a {@code Map<selection, latest>} holds only the newest update per selection (so memory
 * is bounded by distinct selections, not by the number of offers), and an {@link ArrayDeque} of
 * pending selections gives {@code poll()} a stable FIFO order. A single {@link ReentrantLock} makes
 * the offer's compare-keep-highest and the poll's remove-and-return atomic across the two threads.
 */
public final class ConflatingBuffer {

    private final Map<String, OddsUpdate> latest = new HashMap<>();
    private final ArrayDeque<String> pending = new ArrayDeque<>(); // selections awaiting a poll, in order
    private final ReentrantLock lock = new ReentrantLock();

    public ConflatingBuffer() {
    }

    public void offer(OddsUpdate update) {
        lock.lock();
        try {
            String sel = update.selection();
            OddsUpdate current = latest.get(sel);
            if (current == null) {
                latest.put(sel, update);
                pending.addLast(sel); // newly pending — enqueue once
            } else if (update.seq() > current.seq()) {
                latest.put(sel, update); // conflate in place; already queued, keep its position
            }
            // else: not newer than what's buffered → ignore
        } finally {
            lock.unlock();
        }
    }

    public Optional<OddsUpdate> poll() {
        lock.lock();
        try {
            String sel = pending.pollFirst();
            if (sel == null) {
                return Optional.empty();
            }
            return Optional.of(latest.remove(sel));
        } finally {
            lock.unlock();
        }
    }

    public int pending() {
        lock.lock();
        try {
            return latest.size();
        } finally {
            lock.unlock();
        }
    }
}
