package org.kata.parking;

/**
 * Sealed sibling hierarchy modelling the closed set of parking-spot kinds.
 *
 * <h2>Why sealed siblings instead of inheritance — the Liskov story</h2>
 *
 * <p>The instinctive OO design is a class hierarchy: {@code StandardSpot extends CompactSpot},
 * {@code EVSpot extends StandardSpot}, etc. This reads naturally ("an EV spot is a kind of
 * standard spot"), but it is a lie that the type system will happily let you tell.
 *
 * <p>The Liskov Substitution Principle says: anywhere a {@code StandardSpot} is expected, an
 * {@code EVSpot} must be substitutable without breaking the contract. The contract of
 * {@code StandardSpot.fits} accepts both {@code CAR} and {@code EV}. But an {@code EVSpot}
 * only accepts {@code EV} — it <em>strengthens the precondition</em>. A caller who holds a
 * {@code StandardSpot} reference, parks a regular {@code CAR}, and is silently handed an
 * {@code EVSpot} subtype will be rejected. The "is-a" relationship is false; substitutability
 * fails; LSP is violated.
 *
 * <p>The honest model is: these are four <em>peer</em> kinds of spot with overlapping but
 * non-nested {@code fits} rules. A sealed interface with record permits captures exactly
 * that: a closed, exhaustive set of siblings, each with its own rule, no inheritance lie,
 * and the compiler enforces exhaustive {@code switch} when we pattern-match. This is the
 * single most-asked OOP question in senior Java interviews — recognise it and name it.
 *
 * <h2>The contract</h2>
 * <ul>
 *   <li>{@link #fits} — does this spot physically accept this vehicle? Each implementation
 *       answers for itself; there is no inherited default to be (mis)overridden.</li>
 *   <li>{@link #sizeRank} — total order over spot capacity. Lower means more constrained
 *       (compact &lt; standard/EV &lt; truck). Drives best-fit allocation: assign the
 *       smallest spot that fits so we don't waste a truck bay on a motorcycle.</li>
 * </ul>
 */
public sealed interface Spot permits CompactSpot, StandardSpot, EVSpot, TruckSpot {
    int id();
    boolean fits(Vehicle vehicle);
    /** For best-fit ordering — smaller is more constrained. */
    int sizeRank();
}
