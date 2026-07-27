package org.kata.feedparser;

/**
 * The outcome of parsing one non-skipped feed line: either a good {@link Ok} (a {@link Quote} plus
 * the physical line it came from) or a bad {@link Err} (a {@link ParseError}).
 *
 * <p>This is a classic <b>result type</b> — the parse of a line is a value, not an exception. Errors
 * on a streaming feed are <i>expected</i>, not exceptional: a single fat-fingered tick must never
 * tear down the stream and lose the thousand good ticks behind it. Returning a {@code ParsedLine}
 * per line keeps every outcome in-band so the caller decides what to do with each.
 *
 * <p>Modelled as a {@code sealed interface} with exactly two permitted implementations so the
 * compiler can <b>enforce exhaustive handling</b>: a {@code switch} over a {@code ParsedLine} that
 * forgets the {@code Err} arm won't compile (no {@code default} needed, and record patterns can
 * destructure each arm). That exhaustiveness is the whole reason to prefer a sealed type over, say,
 * a nullable {@code Quote} plus a side-channel error list — the type system guarantees no outcome is
 * dropped on the floor.
 */
public sealed interface ParsedLine permits ParsedLine.Ok, ParsedLine.Err {

    /**
     * A line that parsed cleanly. Carries the {@code line} it came from (1-based, physical) so a
     * caller streaming {@code Ok}s can still report positions without re-deriving them.
     */
    record Ok(int line, Quote quote) implements ParsedLine {
    }

    /** A line that failed to parse. The {@link ParseError} carries both the line and the kind. */
    record Err(ParseError error) implements ParsedLine {
    }
}
