package org.kata.vending;

import java.math.BigDecimal;
import java.util.List;

/**
 * Outcome of a {@link VendingMachine#select(String)} attempt.
 *
 * <p><b>Why a sealed result type instead of exceptions?</b> Out-of-stock, insufficient funds and
 * "can't make change" are <em>not exceptional</em> — they are expected branches in the business
 * flow that any caller has to reason about. Modelling them as throws turns predictable control
 * flow into invisible control flow, and exceptions in Java are also expensive to construct
 * (stack-trace capture) for outcomes that happen on the happy path of "user didn't put in
 * enough money yet".
 *
 * <p>The {@code sealed} keyword (Java 17+) plus the named record variants gives us a closed
 * algebraic sum type: the compiler knows the exhaustive list of possible results, so a
 * {@code switch} on a {@code DispenseResult} can be checked for exhaustiveness. Adding a new
 * variant becomes a compile-time prompt to update every caller — the type system surfaces the
 * work instead of waiting for a runtime surprise.
 *
 * <p>Each variant below documents the producing condition and the recommended caller action.
 */
public sealed interface DispenseResult {

    /**
     * Happy path. The product was dispensed and any excess funds were returned as coins.
     *
     * <p>Producing condition: stock available, sufficient funds inserted, and the projected
     * coin inventory could make exact change.
     *
     * <p>Caller action: hand the product and change to the user; session has already been reset.
     */
    record Dispensed(Product product, List<Coin> change) implements DispenseResult {}

    /**
     * The user hasn't paid enough yet. {@code needed} is the remaining shortfall.
     *
     * <p>Producing condition: {@code insertedAmount < product.price}. Session is <em>kept open</em>
     * — the user can keep feeding coins and re-attempt {@code select()}.
     *
     * <p>Caller action: prompt the user to insert at least {@code needed} more; do not refund.
     */
    record InsufficientFunds(BigDecimal needed) implements DispenseResult {}

    /**
     * The requested slot is empty.
     *
     * <p>Producing condition: stock count for {@code productCode} is zero.
     *
     * <p>Caller action: surface the message and accept the auto-refund that has already been
     * issued; pick another product or walk away.
     */
    record OutOfStock(String productCode) implements DispenseResult {}

    /**
     * The slot code doesn't exist in the catalogue.
     *
     * <p>Producing condition: typo / unconfigured button. Session is auto-refunded.
     *
     * <p>Caller action: show "invalid selection"; coins are already returned.
     */
    record UnknownProduct(String productCode) implements DispenseResult {}

    /**
     * The user paid enough, but the machine cannot produce the exact change owed from its
     * projected coin inventory (the existing float plus the coins just inserted).
     *
     * <p>Producing condition: greedy change planning fails to reach zero remaining. With
     * canonical denominations this only happens when a specific coin is depleted (e.g. owe
     * 5c but no nickels and no pennies).
     *
     * <p>Caller action: the session has been auto-refunded — apologise and ask the operator
     * to top up coins. Critically, we refund <em>before</em> dispensing rather than dispense
     * and short-change the customer.
     */
    record CannotMakeChange(BigDecimal owed) implements DispenseResult {}
}
