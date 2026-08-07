package org.kata.positionkeeper;

/**
 * Which side of the market a bet was placed on.
 *
 * <p>{@code BACK} profits {@code stake * (odds - 1)} if the selection wins and loses the stake
 * otherwise. {@code LAY} is the mirror image — the layer (the exchange counterparty) profits the
 * stake if the selection loses and pays out {@code stake * (odds - 1)} if it wins.
 */
public enum Side {
    BACK,
    LAY
}
