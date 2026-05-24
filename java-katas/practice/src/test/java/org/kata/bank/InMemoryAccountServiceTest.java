package org.kata.bank;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryAccountServiceTest {

    private final AccountService service = new InMemoryAccountService();

    @Test
    void deposit_increases_balance() {
        var acc = service.open(new BigDecimal("100"));
        var updated = service.deposit(acc.id(), new BigDecimal("50")).orElseThrow();
        assertEquals(new BigDecimal("150"), updated.balance());
    }

    @Test
    void withdraw_decreases_balance() {
        var acc = service.open(new BigDecimal("100"));
        var updated = service.withdraw(acc.id(), new BigDecimal("30")).orElseThrow();
        assertEquals(new BigDecimal("70"), updated.balance());
    }

    @Test
    void withdraw_rejects_overdraft() {
        var acc = service.open(new BigDecimal("100"));
        assertFalse(service.withdraw(acc.id(), new BigDecimal("200")).isPresent());
        assertEquals(new BigDecimal("100"), service.find(acc.id()).orElseThrow().balance());
    }

    @Test
    void transfer_moves_money_atomically() {
        var alice = service.open(new BigDecimal("100"));
        var bob = service.open(new BigDecimal("50"));
        assertTrue(service.transfer(alice.id(), bob.id(), new BigDecimal("30")));
        assertEquals(new BigDecimal("70"), service.find(alice.id()).orElseThrow().balance());
        assertEquals(new BigDecimal("80"), service.find(bob.id()).orElseThrow().balance());
    }

    @Test
    void transfer_rejects_insufficient_funds() {
        var alice = service.open(new BigDecimal("10"));
        var bob = service.open(new BigDecimal("50"));
        assertFalse(service.transfer(alice.id(), bob.id(), new BigDecimal("100")));
        // Neither balance changed
        assertEquals(new BigDecimal("10"), service.find(alice.id()).orElseThrow().balance());
        assertEquals(new BigDecimal("50"), service.find(bob.id()).orElseThrow().balance());
    }

    @Test
    void transfer_rejects_same_account() {
        var acc = service.open(new BigDecimal("100"));
        assertFalse(service.transfer(acc.id(), acc.id(), new BigDecimal("10")));
    }

    @Test
    void negative_amounts_throw() {
        var acc = service.open(new BigDecimal("100"));
        assertThrows(IllegalArgumentException.class,
                () -> service.deposit(acc.id(), new BigDecimal("-1")));
    }
}
