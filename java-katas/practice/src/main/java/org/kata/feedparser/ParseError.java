package org.kata.feedparser;

public record ParseError(int line, ErrorKind kind) {
}
