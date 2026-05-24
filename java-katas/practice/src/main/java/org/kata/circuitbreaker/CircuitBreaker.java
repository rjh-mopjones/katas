package org.kata.circuitbreaker;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public class CircuitBreaker<T,R> {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private State state = State.CLOSED;

    private final int failureThreshold;

    private final int successThreshold;

    private final int retryThreshold;

    private AtomicInteger retries = new AtomicInteger(0);
    private AtomicInteger successes = new AtomicInteger(0);
    private AtomicInteger failures = new AtomicInteger(0);

    private final Function<T, R> functionToCall;

    private final ReentrantLock lock;

    public CircuitBreaker(int failureThreshold, int retryThreshold, int successThreshold, Function<T, R> functionToCall) {
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.retryThreshold = retryThreshold;
        this.functionToCall = functionToCall;
        lock = new ReentrantLock();
    }

    public State state() {
        return state;
    }

    public R execute(T param) {
        State currentState = this.state;
        if (currentState.equals(State.OPEN)) {
            evaluateLock(currentState,true);
            throw new CircuitOpenException("Circuit Is Open");
        }
        try {
            R result = functionToCall.apply(param);
            evaluateLock(currentState, true);
            return result;
        } catch (Exception e){
            evaluateLock(currentState, false);
            throw e;
        }
    }

    private void evaluateLock(State state, boolean success) {
        lock.lock();
        try {
            if (state.equals(State.OPEN)){
                int currentRetries = 0;
                if (success){
                   currentRetries = retries.incrementAndGet();
                } else {
                    retries.set(0);
                }
                if (currentRetries == retryThreshold){
                    this.state = State.HALF_OPEN;
                }
            } else if (state.equals(State.HALF_OPEN)){
                int currentSuccesses = 0;
                if (success){
                    currentSuccesses = successes.incrementAndGet();
                } else {
                    successes.set(0);
                }
                if (currentSuccesses == successThreshold){
                    this.state = State.CLOSED;
                }
            } else if (state.equals(State.CLOSED)){
                int currentFailures = 0;
                if (!success) {
                    currentFailures = failures.incrementAndGet();
                }
                if (currentFailures == failureThreshold){
                    this.state = State.OPEN;
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
