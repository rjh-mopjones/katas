package org.kata.vending;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class VendingMachineTest {

    private VendingMachine vm;
    private final Product cola = new Product("A1", "Cola", new BigDecimal("1.25"));

    @BeforeEach
    void setUp() {
        vm = new VendingMachine();
        vm.restock(cola, 3);
        vm.loadCoins(Coin.QUARTER, 10);
        vm.loadCoins(Coin.DIME, 10);
        vm.loadCoins(Coin.NICKEL, 10);
    }

    @Test
    void dispenses_when_exact_money_inserted() {
        vm.insertCoin(Coin.DOLLAR);
        vm.insertCoin(Coin.QUARTER);
        var result = vm.select("A1");
        assertInstanceOf(DispenseResult.Dispensed.class, result);
        var d = (DispenseResult.Dispensed) result;
        assertEquals(cola, d.product());
        assertTrue(d.change().isEmpty());
    }

    @Test
    void dispenses_with_change() {
        vm.insertCoin(Coin.DOLLAR);
        vm.insertCoin(Coin.DOLLAR);   // overpay by 0.75
        var result = vm.select("A1");
        var d = assertInstanceOf(DispenseResult.Dispensed.class, result);
        BigDecimal changeTotal = d.change().stream()
                .map(Coin::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, changeTotal.compareTo(new BigDecimal("0.75")));
    }

    @Test
    void insufficient_funds_keeps_session_alive() {
        vm.insertCoin(Coin.QUARTER);
        var result = vm.select("A1");
        var ins = assertInstanceOf(DispenseResult.InsufficientFunds.class, result);
        assertEquals(0, ins.needed().compareTo(new BigDecimal("1.00")));
    }

    @Test
    void unknown_product_refunds() {
        vm.insertCoin(Coin.DOLLAR);
        var result = vm.select("ZZ");
        assertInstanceOf(DispenseResult.UnknownProduct.class, result);
    }

    @Test
    void out_of_stock_refunds() {
        VendingMachine empty = new VendingMachine();
        empty.restock(cola, 0);
        empty.insertCoin(Coin.DOLLAR);
        empty.insertCoin(Coin.QUARTER);
        var result = empty.select("A1");
        assertInstanceOf(DispenseResult.OutOfStock.class, result);
    }

    @Test
    void refund_returns_inserted_coins() {
        vm.insertCoin(Coin.QUARTER);
        vm.insertCoin(Coin.DIME);
        var coins = vm.refund();
        assertEquals(2, coins.size());
    }
}
