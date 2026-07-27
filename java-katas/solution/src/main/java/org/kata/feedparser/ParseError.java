package org.kata.feedparser;

/**
 * A single malformed feed line: <i>what</i> was wrong ({@link ErrorKind}) and <i>where</i>
 * ({@code line}).
 *
 * <p>{@code line} is the <b>1-based physical line number</b> in the source feed — counting every
 * line, including blank and comment lines that are themselves skipped. This is deliberate: when an
 * operator eyeballs the raw feed to diagnose a bad tick, "line 6" must mean the sixth line of the
 * file they are looking at, not the sixth <i>record</i>. Threading the physical number through the
 * pipeline (rather than a running record index) is what makes the error actionable.
 *
 * <p>A {@code record} for value-equality so a test can assert an exact
 * {@code new ParseError(6, ErrorKind.INVALID_BID)} rather than probing fields.
 */
public record ParseError(int line, ErrorKind kind) {
}
