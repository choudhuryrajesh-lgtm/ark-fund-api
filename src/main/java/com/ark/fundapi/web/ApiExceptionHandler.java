package com.ark.fundapi.web;

import com.ark.fundapi.exception.BusinessRuleException;
import com.ark.fundapi.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates exceptions into RFC 7807 {@code application/problem+json}
 * responses so every error shares one machine-readable shape.
 *
 * <p>Consistent error contracts matter as much as the success contract —
 * clients should never have to parse a stack trace or guess at a bespoke
 * per-endpoint error body.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), "resource-not-found");
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        return problem(HttpStatus.CONFLICT, "Business rule violation", ex.getMessage(), "business-rule-violation");
    }

    /** Bean-validation failures — reported per field so a client can fix all of them at once. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid", "validation-failed");
        detail.setProperty("errors", fieldErrors);
        return detail;
    }

    /**
     * A {@code ?sort=} parameter that doesn't name a real field on the sorted
     * entity — e.g. Swagger UI's placeholder value {@code ["string"]} sent
     * as-is. This is client input, not a server fault, so it belongs on 400
     * rather than falling through to the 500 catch-all below.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleInvalidSortProperty(PropertyReferenceException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "'%s' is not a sortable field".formatted(ex.getPropertyName()), "validation-failed");
    }

    /**
     * A query/path parameter that doesn't parse as its declared type — most
     * commonly {@code ?asOfDate=} sent in a non-ISO format (e.g. {@code
     * 2206/08/01} instead of {@code 2026-08-01}), or a malformed UUID path
     * segment. Client input, so 400, not the 500 catch-all below.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expectedType = ex.getRequiredType() == null ? "the expected type" : ex.getRequiredType().getSimpleName();
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "'%s' is not a valid value for '%s'".formatted(ex.getValue(), ex.getName()), "validation-failed");
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        fieldErrors.put(ex.getName(), "must be a valid " + expectedType
                + (isLocalDate(ex) ? " in ISO-8601 format (YYYY-MM-DD)" : ""));
        detail.setProperty("errors", fieldErrors);
        return detail;
    }

    private static boolean isLocalDate(MethodArgumentTypeMismatchException ex) {
        return ex.getRequiredType() != null && ex.getRequiredType().equals(LocalDate.class);
    }

    /**
     * No route matches the request path at all — Spring's own fallback for an
     * unmapped GET. Client input (a bad URL), so 404, not the 500 catch-all
     * below; without this handler it falls through to the generic
     * Exception handler and gets misreported as a 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found",
                "No endpoint matches this path.", "no-such-endpoint");
    }

    /**
     * Catch-all. The client gets a generic message while the real cause goes to
     * the logs — leaking exception internals over HTTP is an information
     * disclosure risk.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception while processing request", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred. Please contact support if this persists.",
                "internal-error");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create("https://api.ark.com/problems/" + type));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
