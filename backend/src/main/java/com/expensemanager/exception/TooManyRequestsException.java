package com.expensemanager.exception;

/** 429: the app's own per-minute limit (e.g. Muneem chat). */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
