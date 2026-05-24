package org.kata.bank;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentAccountServiceTest {

    @Test
    void concurrent_deposits_preserve_total() throws Exception {
        var service = new ConcurrentAccountService();
        var acc = service.open(BigDecimal.ZERO);

        int N = 200;
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, N).forEach(i -> exec.submit(() -> {
                try {
                    gate.await();
                    service.deposit(acc.id(), BigDecimal.ONE);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { done.countDown(); }
            }));
            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }

        assertEquals(new BigDecimal(N), service.find(acc.id()).orElseThrow().balance());
    }

    @Test
    void concurrent_transfers_never_create_or_destroy_money() throws Exception {
        // Headline test: two accounts swap money back and forth in opposite directions.
        // Without lock ordering, threads doing A->B and B->A can deadlock.
        // Invariant: total money in system is constant.
        var service = new ConcurrentAccountService();
        var a = service.open(new BigDecimal("1000"));
        var b = service.open(new BigDecimal("1000"));

        int N = 500;
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < N; i++) {
                boolean aToB = i % 2 == 0;
                exec.submit(() -> {
                    try {
                        gate.await();
                        UUID from = aToB ? a.id() : b.id();
                        UUID to = aToB ? b.id() : a.id();
                        service.transfer(from, to, BigDecimal.ONE);
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    finally { done.countDown(); }
                });
            }
            gate.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS), "deadlock — transfers never finished");
        }

        BigDecimal total = service.find(a.id()).orElseThrow().balance()
                .add(service.find(b.id()).orElseThrow().balance());
        assertEquals(new BigDecimal("2000"), total);
    }

    @Test
    void concurrent_withdraws_never_overdraft() throws Exception {
        // 100 threads each try to withdraw 1 from an account with balance 50.
        // Exactly 50 should succeed, balance ends at 0, no negative.
        var service = new ConcurrentAccountService();
        var acc = service.open(new BigDecimal("50"));

        int N = 100;
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N);
        var successes = new AtomicInteger();

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, N).forEach(i -> exec.submit(() -> {
                try {
                    gate.await();
                    service.withdraw(acc.id(), BigDecimal.ONE).ifPresent(a -> successes.incrementAndGet());
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { done.countDown(); }
            }));
            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }

        assertEquals(50, successes.get());
        assertEquals(BigDecimal.ZERO, service.find(acc.id()).orElseThrow().balance());
    }
}
