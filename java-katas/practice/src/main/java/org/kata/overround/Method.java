package org.kata.overround;

/**
 * De-vig method: how to strip a bookmaker's margin out of a set of implied probabilities so they
 * sum back to exactly 1.0 ("fair" probabilities).
 *
 * <ul>
 *   <li>{@link #PROPORTIONAL} — scale every implied probability down by the same factor
 *       ({@code bookSum}). Simple, always well-behaved, but assumes the margin is loaded onto
 *       every outcome in proportion to its own probability.</li>
 *   <li>{@link #ADDITIVE} — subtract an equal <em>absolute</em> share of the margin from every
 *       outcome. Can drive a longshot's probability negative.</li>
 *   <li>{@link #POWER} — raise every implied probability to a common exponent {@code k} chosen so
 *       the result sums to 1. Reflects the empirical observation that bookmakers load
 *       disproportionately more margin onto longshots (the "favourite-longshot bias").</li>
 * </ul>
 */
public enum Method { PROPORTIONAL, ADDITIVE, POWER }
