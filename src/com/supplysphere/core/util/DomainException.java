package com.supplysphere.core.util;

/**
 * Unchecked exception raised when a supply-chain invariant is violated.
 * Carries a stable machine-readable {@code code} so callers can branch on the
 * failure kind without string matching.
 */
public class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = Preconditions.requireNonBlank(code, "code");
    }

    public DomainException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = Preconditions.requireNonBlank(code, "code");
    }

    public String getCode() {
        return code;
    }

    public static DomainException notFound(String entity, Object id) {
        return new DomainException("NOT_FOUND", entity + " not found for id=" + id);
    }

    public static DomainException duplicate(String entity, Object id) {
        return new DomainException("DUPLICATE_ID", entity + " already registered for id=" + id);
    }

    public static DomainException illegalState(String message) {
        return new DomainException("ILLEGAL_STATE", message);
    }
}
