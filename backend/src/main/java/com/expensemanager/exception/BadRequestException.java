package com.expensemanager.exception;

/** 400 without field errors (bad CSV header, too many rows, bad query param...). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
