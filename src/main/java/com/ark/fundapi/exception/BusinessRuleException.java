package com.ark.fundapi.exception;

/**
 * Thrown when a request is well-formed but violates a business rule — a
 * transaction dated before its fund existed, a duplicate fund name within a
 * client, deleting a fund that still has transactions.
 *
 * <p>Distinct from bean-validation failures (malformed input, surfaced as 400)
 * and from missing resources (404); these map to 409 Conflict.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
