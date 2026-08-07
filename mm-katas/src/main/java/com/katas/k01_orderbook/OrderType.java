package com.katas.k01_orderbook;

/** How an order executes. */
public enum OrderType {
    /** Rests at a stated price; the unmatched remainder stays in the book. */
    LIMIT,
    /** Executes against the best available prices; any unmatched remainder does not rest. */
    MARKET
}
