package org.kata.topk;

/** A key and its cumulative score at the moment it was read out of a {@link TopK}. */
public record Entry(String key, long score) {
}
