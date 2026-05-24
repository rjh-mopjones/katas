package org.kata.bank;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Service-layer contract for account operations. Defined as an interface so the
 * single-threaded {@link InMemoryAccountService} and the thread-safe
 * {@link ConcurrentAccountService} can be swapped behind the same API — useful for
 * isolating the concurrency concern from the business logic in tests and reviews.
 *
 * <p>All amounts are {@link BigDecimal} to avoid IEEE-754 float drift on money; see
 * {@link Account} for the full rationale.
 */
public interface AccountService {

    /**
     * Creates a new account with a freshly minted id and the supplied opening balance.
     * Opening balance must be non-negative (enforced by {@link Account}'s invariants).
     */
    Account open(BigDecimal openingBalance);

    /**
     * Looks up an account by id. Returns empty if no such account exists — callers
     * decide whether absence is a domain error or simply a miss.
     */
    Optional<Account> find(UUID accountId);

    /**
     * Credits {@code amount} to the named account. Amount must be strictly positive
     * (zero and negative are rejected as programmer errors via {@link IllegalArgumentException}).
     * Returns the updated account, or empty if the account does not exist.
     */
    Optional<Account> deposit(UUID accountId, BigDecimal amount);

    /**
     * Returns the updated account, or empty if account missing OR insufficient funds.
     * Distinguishing the two cases would warrant a sealed {@code Result} type — kept Optional for brevity.
     *
     * <p><b>Why {@link Optional} and not an exception.</b> Insufficient funds is an expected
     * domain outcome on a withdrawal, not an exceptional condition. Reserving exceptions for
     * truly exceptional cases (programmer bugs, infrastructure failures) keeps the control
     * flow honest and makes the happy/sad paths visible at the call site.
     */
    Optional<Account> withdraw(UUID accountId, BigDecimal amount);

    /**
     * Transfers atomically. Returns true only if both legs succeeded.
     * The concurrent implementation enforces lock-ordering by accountId to prevent deadlock.
     *
     * <p>Atomicity here means: external observers never see a state where money has left
     * {@code from} but not yet arrived at {@code to}. If the withdrawal would overdraw,
     * neither side moves and the method returns false.
     */
    boolean transfer(UUID from, UUID to, BigDecimal amount);
}
