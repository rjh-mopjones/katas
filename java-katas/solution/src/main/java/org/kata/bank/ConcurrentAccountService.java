package org.kata.bank;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe variant.
 *
 * <p><b>Headline pattern: deadlock-free transfer via monotonic lock ordering.</b>
 * Two-account transactions acquire locks in a globally consistent order (here, the natural
 * order of the {@link UUID}s) regardless of the transfer direction.
 *
 * <p><b>Why this works.</b> Deadlock requires a cycle in the lock-acquisition graph. The
 * classic deadlock is two threads each holding one lock and waiting for the other:
 * thread T1 doing {@code transfer(A, B)} grabs {@code lock(A)} then tries {@code lock(B)};
 * thread T2 doing {@code transfer(B, A)} grabs {@code lock(B)} then tries {@code lock(A)}.
 * Neither can proceed. By forcing <i>every</i> transfer to acquire {@code min(from, to)}
 * first and {@code max(from, to)} second, both threads above will try for the same lock
 * first — one wins, the other waits, and the cycle cannot form. This is the single
 * most-asked concurrency pattern in fintech interviews; know it cold.
 *
 * <p><b>Alternatives worth naming in interview.</b>
 * <ul>
 *   <li>{@link ReentrantLock#tryLock()} with timeout and retry — avoids the ordering
 *       requirement but introduces livelock risk and back-off complexity.</li>
 *   <li>A single global lock — trivially correct, terrible throughput.</li>
 *   <li>STM / optimistic concurrency with retries — fine when contention is low.</li>
 *   <li>Single-writer thread with a command queue — sidesteps locking entirely.</li>
 * </ul>
 *
 * <p>Per-account {@link ReentrantLock}s are created on demand for any account id seen,
 * keyed in a {@link ConcurrentHashMap} via {@code computeIfAbsent} so two threads racing
 * to create the same lock end up sharing one.
 */
public class ConcurrentAccountService implements AccountService {

    // ConcurrentHashMap rather than HashMap: lookups and inserts happen concurrently with
    // mutations on other keys. The map itself must be safe; the per-account locks below
    // protect the read-modify-write sequences against races on the same key.
    private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();
    private final Map<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    // computeIfAbsent is atomic on ConcurrentHashMap — guarantees one lock per id even
    // under concurrent first-touch from multiple threads. Without it, two threads could
    // each instantiate a separate lock and silently lose mutual exclusion.
    private ReentrantLock lockFor(UUID id) {
        return locks.computeIfAbsent(id, k -> new ReentrantLock());
    }

    @Override
    public Account open(BigDecimal openingBalance) {
        Account a = new Account(UUID.randomUUID(), openingBalance);
        accounts.put(a.id(), a);
        // Pre-create the lock so subsequent operations don't race on first acquisition.
        lockFor(a.id());
        return a;
    }

    @Override
    public Optional<Account> find(UUID accountId) {
        // Unlocked read: ConcurrentHashMap.get sees a consistent snapshot of the reference,
        // and Account itself is immutable, so the caller gets a coherent value. We trade
        // a possibly-stale read for zero contention — appropriate for a query method.
        return Optional.ofNullable(accounts.get(accountId));
    }

    @Override
    public Optional<Account> deposit(UUID accountId, BigDecimal amount) {
        requirePositive(amount);
        ReentrantLock lock = lockFor(accountId);
        lock.lock();
        try {
            Account a = accounts.get(accountId);
            if (a == null) return Optional.empty();
            Account updated = a.withBalance(a.balance().add(amount));
            accounts.put(updated.id(), updated);
            return Optional.of(updated);
        } finally {
            // try/finally around the critical section: an exception inside must not leave
            // the lock held, or the account becomes permanently unusable.
            lock.unlock();
        }
    }

    @Override
    public Optional<Account> withdraw(UUID accountId, BigDecimal amount) {
        requirePositive(amount);
        ReentrantLock lock = lockFor(accountId);
        lock.lock();
        try {
            Account a = accounts.get(accountId);
            if (a == null) return Optional.empty();
            BigDecimal newBalance = a.balance().subtract(amount);
            if (newBalance.signum() < 0) return Optional.empty();
            Account updated = a.withBalance(newBalance);
            accounts.put(updated.id(), updated);
            return Optional.of(updated);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean transfer(UUID from, UUID to, BigDecimal amount) {
        if (from.equals(to)) return false;
        requirePositive(amount);

        // Acquire both locks in monotonic order. Same order regardless of which way the
        // transfer goes, so two threads doing A->B and B->A can't deadlock.
        // UUID.compareTo defines a total order over all ids — that's all the ordering
        // function has to satisfy. Any deterministic total order would work (System.identityHashCode
        // is a common alternative, with a tiebreaker for collisions).
        UUID first = from.compareTo(to) < 0 ? from : to;
        UUID second = first.equals(from) ? to : from;
        ReentrantLock lockFirst = lockFor(first);
        ReentrantLock lockSecond = lockFor(second);

        lockFirst.lock();
        try {
            lockSecond.lock();
            try {
                // Both locks held — read source and destination, validate, mutate. No
                // other transfer touching either account can interleave with this block.
                Account src = accounts.get(from);
                Account dst = accounts.get(to);
                if (src == null || dst == null) return false;
                BigDecimal newSrc = src.balance().subtract(amount);
                if (newSrc.signum() < 0) return false;
                // Both writes happen under the same locks: external observers either see
                // the pre-transfer state or the post-transfer state, never the gap.
                accounts.put(from, src.withBalance(newSrc));
                accounts.put(to, dst.withBalance(dst.balance().add(amount)));
                return true;
            } finally {
                // Unlock in reverse order of acquisition. Not strictly required for
                // correctness with ReentrantLock, but matches the LIFO convention and
                // keeps the pattern symmetric with lock-acquisition order.
                lockSecond.unlock();
            }
        } finally {
            lockFirst.unlock();
        }
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("amount must be positive");
    }
}
