package com.ark.fundapi.exception;

/**
 * Thrown by a Resilience4j circuit breaker fallback method when the breaker
 * is open — RDS (or whatever the guarded call depends on) is failing
 * repeatedly, so this call is rejected immediately rather than piling onto
 * an already-struggling dependency. Maps to 503, not 500: this is a known,
 * intentional protective state, not an unexpected server fault.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
