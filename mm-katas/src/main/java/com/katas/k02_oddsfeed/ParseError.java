package com.katas.k02_oddsfeed;

/**
 * A malformed line, surfaced to the listener rather than thrown. A bad line must never abort the
 * stream or desync the parser's buffer.
 *
 * @param lineNumber 1-based line number, a running counter across every {@code feed()} call
 * @param rawLine    the offending line exactly as received (without its line terminator)
 * @param reason     a human-readable explanation (wrong field count / bad seq / bad odds)
 */
public record ParseError(long lineNumber, String rawLine, String reason) {
}
