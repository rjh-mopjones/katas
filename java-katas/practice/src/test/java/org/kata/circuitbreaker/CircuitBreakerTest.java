package org.kata.circuitbreaker;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
class CircuitBreakerTest {

    Function<Boolean, Boolean> FUNCTION = (failing) -> {
        if (!failing) {
            throw new RuntimeException("function failed");
        }
        return Boolean.TRUE;
    };

    @Test
    void execute_works() throws Exception {
        CircuitBreaker<Boolean, Boolean> circuitBreaker =
                new CircuitBreaker<>(1,1,1, FUNCTION);

        assertTrue(circuitBreaker.execute(Boolean.TRUE));
        assertThrows(RuntimeException.class, () -> circuitBreaker.execute(Boolean.FALSE));
    }

    @Test
    void breaker_trips_open_after_failure() throws Exception {
        CircuitBreaker<Boolean, Boolean> circuitBreaker =
                new CircuitBreaker<>(2,2,2, FUNCTION);

        assertTrue(circuitBreaker.execute(Boolean.TRUE));
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state());
        assertThrows(RuntimeException.class, () -> circuitBreaker.execute(Boolean.FALSE));
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state());
        assertThrows(RuntimeException.class, () -> circuitBreaker.execute(Boolean.FALSE));
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state());
    }

    @Test
    void breaker_trips_half_open_after_success() throws Exception {
        CircuitBreaker<Boolean, Boolean> circuitBreaker =
                new CircuitBreaker<>(1,1,2, FUNCTION);

        assertTrue(circuitBreaker.execute(Boolean.TRUE));
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state());
        assertThrows(RuntimeException.class, () -> circuitBreaker.execute(Boolean.FALSE));
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state());
        assertThrows(CircuitOpenException.class, () -> circuitBreaker.execute(Boolean.TRUE));
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.state());
    }

    @Test
    void breaker_trips_closed_after_success() throws Exception {
        CircuitBreaker<Boolean, Boolean> circuitBreaker =
                new CircuitBreaker<>(1,1,2, FUNCTION);

        assertTrue(circuitBreaker.execute(Boolean.TRUE));
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state());
        assertThrows(RuntimeException.class, () -> circuitBreaker.execute(Boolean.FALSE));
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state());
        assertThrows(CircuitOpenException.class, () -> circuitBreaker.execute(Boolean.TRUE));
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.state());
        assertTrue(circuitBreaker.execute(Boolean.TRUE));
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.state());
        assertTrue(circuitBreaker.execute(Boolean.TRUE));
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state());
    }

    @Test
    void concurrent_calls_never_exceed_failure_threshold() throws Exception {

        AtomicInteger functionCalls = new AtomicInteger();

        Function<Boolean, Boolean> trackingFunction = (failing) -> {
            functionCalls.incrementAndGet();
            if (!failing) {
                throw new RuntimeException("function failed");
            }
            return Boolean.TRUE;
        };

        var breaker = new CircuitBreaker<>(5, 1, 1, trackingFunction);
        int threads = 100;

        AtomicInteger circuitOpenExceptions = new AtomicInteger();

        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()){
            for (int i = 0 ; i < threads; i++){
                executor.submit(()->{
                    try {
                        gate.await();
                        breaker.execute(Boolean.FALSE);
                    } catch (CircuitOpenException e){
                        circuitOpenExceptions.incrementAndGet();
                    } catch (InterruptedException e) {
                    } finally {
                        done.countDown();
                    }
                });
            }
            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }
        assertEquals(5, functionCalls.get());
        assertEquals(95, circuitOpenExceptions.get());
    }
}