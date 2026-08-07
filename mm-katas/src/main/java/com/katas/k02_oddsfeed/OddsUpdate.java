package com.katas.k02_oddsfeed;

import java.math.BigDecimal;

/**
 * One parsed price line from a bookmaker's odds feed.
 *
 * <p>{@code seq} is the bookmaker's own monotonically-increasing sequence number (used to detect
 * gaps and drop stale duplicates). Odds are a {@link BigDecimal} — decimal odds are exact prices and
 * must never be compared as {@code double}.
 *
 * @param seq       the bookmaker's sequence number for this update
 * @param book      the bookmaker (e.g. {@code "BET365"})
 * @param event     the event (e.g. {@code "ARS_v_CHE"})
 * @param market    the market on the event (e.g. {@code "MATCH_ODDS"})
 * @param selection the runner/selection within the market (e.g. {@code "HOME"})
 * @param odds      the quoted decimal odds
 */
public record OddsUpdate(long seq, String book, String event, String market, String selection, BigDecimal odds) {
}
