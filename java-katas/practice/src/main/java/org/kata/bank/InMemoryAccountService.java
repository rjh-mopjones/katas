package org.kata.bank;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryAccountService implements AccountService {

    @Override
    public Account open(BigDecimal openingBalance) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Account> find(UUID accountId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Account> deposit(UUID accountId, BigDecimal amount) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Account> withdraw(UUID accountId, BigDecimal amount) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean transfer(UUID from, UUID to, BigDecimal amount) {
        throw new UnsupportedOperationException();
    }
}
