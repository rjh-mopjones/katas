package org.kata.vending;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VendingMachine {

    public VendingMachine() {
        throw new UnsupportedOperationException();
    }

    public synchronized void restock(Product product, int qty) {
        throw new UnsupportedOperationException();
    }

    public synchronized void loadCoins(Coin coin, int count) {
        throw new UnsupportedOperationException();
    }

    public synchronized void insertCoin(Coin coin) {
        throw new UnsupportedOperationException();
    }

    public synchronized DispenseResult select(String code) {
        throw new UnsupportedOperationException();
    }

    public synchronized List<Coin> refund() {
        throw new UnsupportedOperationException();
    }
}
