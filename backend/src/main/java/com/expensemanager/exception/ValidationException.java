package com.expensemanager.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 400 with per-field messages, e.g. {"amount": "must be > 0"}. */
public class ValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors)); // keeps form order
    }

    public ValidationException(String field, String fieldMessage) {
        this("Validation failed", Map.of(field, fieldMessage));
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
