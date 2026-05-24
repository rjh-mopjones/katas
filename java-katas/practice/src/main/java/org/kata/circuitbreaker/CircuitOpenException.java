package org.kata.circuitbreaker;

/**
 * Thrown when a {@link CircuitBreaker} is in the {@code OPEN} state and fast-rejects a call
 * without invoking the underlying action.
 *
 * <p>This is an unchecked exception by deliberate design. The circuit-open condition is a
 * transient operational state, not a domain error. Checked exceptions would force every call
 * site to handle it even when the caller has no meaningful recovery beyond "try later" —
 * which is exactly what the circuit breaker itself enforces once the open duration elapses.
 *
 * <p>Callers that need to distinguish circuit-open from a genuine failure thrown by the action
 * can catch this type specifically before catching the broader {@link Exception}:
 * <pre>{@code
 * try {
 *     result = breaker.call(action);
 * } catch (CircuitOpenException e) {
 *     return cachedFallback();   // degrade gracefully
 * } catch (Exception e) {
 *     log.warn("action failed", e);
 *     throw e;
 * }
 * }</pre>
 */
public class CircuitOpenException extends RuntimeException {

    /**
     * @param message a human-readable reason, typically including the breaker's name and the
     *                instant at which it will next allow a trial call.
     */
    public CircuitOpenException(String message) {
        super(message);
    }
}
