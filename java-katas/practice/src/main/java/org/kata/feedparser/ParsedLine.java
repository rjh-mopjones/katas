package org.kata.feedparser;

public sealed interface ParsedLine permits ParsedLine.Ok, ParsedLine.Err {

    record Ok(int line, Quote quote) implements ParsedLine {
    }

    record Err(ParseError error) implements ParsedLine {
    }
}
