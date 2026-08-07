package org.kata.circuitbreaker;

public class CircuitBreakerException extends RuntimeException {
  public CircuitBreakerException(String message) {
    super(message);
  }
}
