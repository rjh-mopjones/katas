package org.kata.bank;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Single-threaded reference implementation. Not thread-safe — see {@link ConcurrentAccountService}.
 *
 * <p>This exists as the simplest correct implementation of the business rules, free of any
 * concurrency machinery. In an interview, build this first: it nails down the semantics
 * (positive-amount validation, insufficient-funds handling, transfer atomicity at the
 * functional level) before you complicate the picture with locks. The concurrent version
 * is then a mechanical wrapping of these same rules in a locking strategy.
 *
 * <p>Backed by a plain {@link HashMap} — deliberate; using {@link java.util.concurrent.ConcurrentHashMap}
 * here would imply thread-safety guarantees this class does not make.
 */
public class InMemoryAccountService implements AccountService {

    private final Map<UUID, Account> accounts = new HashMap<>();

    @Override
    public Account open(BigDecimal openingBalance) {
        Account a = new Account(UUID.randomUUID(), openingBalance);
        accounts.put(a.id(), a);
        return a;
    }

    @Override
    public Optional<Account> find(UUID accountId) {
        return Optional.ofNullable(accounts.get(accountId));
    }

    @Override
    public Optional<Account> deposit(UUID accountId, BigDecimal amount) {
        requirePositive(amount);
        Account a = accounts.get(accountId);
        if (a == null) return Optional.empty();
        // Functional update: build a new Account and replace the map entry. The old
        // Account object is now garbage; no caller holding a stale reference can be
        // confused into thinking the balance changed under them.
        Account updated = a.withBalance(a.balance().add(amount));
        accounts.put(updated.id(), updated);
        return Optional.of(updated);
    }

    @Override
    public Optional<Account> withdraw(UUID accountId, BigDecimal amount) {
        requirePositive(amount);
        Account a = accounts.get(accountId);
        if (a == null) return Optional.empty();
        BigDecimal newBalance = a.balance().subtract(amount);
        // Overdraft check uses signum() rather than compareTo(ZERO) — clearer intent and
        // one method call instead of two. Returning empty signals a domain-level refusal,
        // not an error: the caller asked for something legitimate that the account can't satisfy.
        if (newBalance.signum() < 0) return Optional.empty();
        Account updated = a.withBalance(newBalance);
        accounts.put(updated.id(), updated);
        return Optional.of(updated);
    }

    @Override
    public boolean transfer(UUID from, UUID to, BigDecimal amount) {
        if (from.equals(to)) return false;
        requirePositive(amount);
        // Compose the two legs via Optional. The deposit only fires if the withdrawal
        // produced a value, so an insufficient-funds withdrawal short-circuits cleanly.
        // In the single-threaded world this is genuinely atomic — no other thread can
        // observe the in-between state. The concurrent version needs explicit locking.
        return withdraw(from, amount)
                .flatMap(w -> deposit(to, amount))
                .isPresent();
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("amount must be positive");
    }
}
