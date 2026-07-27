package org.kata.feedparser;

public record Quote(String symbol, double bid, double ask, long qty) {
}
