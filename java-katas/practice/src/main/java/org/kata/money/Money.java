package org.kata.money;

import java.math.BigDecimal;

public final class Money implements Comparable<Money> {

    public Money(BigDecimal amount, String currency) {
        throw new UnsupportedOperationException();
    }

    public static Money of(String amount, String currency) {
        throw new UnsupportedOperationException();
    }

    public Money plus(Money other) {
        throw new UnsupportedOperationException();
    }

    public Money minus(Money other) {
        throw new UnsupportedOperationException();
    }

    public Money times(BigDecimal factor) {
        throw new UnsupportedOperationException();
    }

    public BigDecimal amount() {
        throw new UnsupportedOperationException();
    }

    public String currency() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(Money other) {
        throw new UnsupportedOperationException();
    }
}
