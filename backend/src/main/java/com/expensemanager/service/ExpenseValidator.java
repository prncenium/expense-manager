package com.expensemanager.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Field rules shared by manual add and CSV import (SPEC §3.10). Returns field → message,
 * e.g. {"amount": "must be > 0"}; empty map means valid.
 */
@Component
public class ExpenseValidator {

    public static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000.00"); // ₹1,00,00,000
    public static final int VENDOR_MAX = 150;
    public static final int DESCRIPTION_MAX = 500;

    private final Clock clock;

    public ExpenseValidator(Clock clock) {
        this.clock = clock;
    }

    public Map<String, String> validate(LocalDate date, BigDecimal amount, String vendor, String description) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (date == null) {
            errors.put("date", "is required");
        } else if (date.isAfter(LocalDate.now(clock))) {
            errors.put("date", "cannot be in the future");
        }

        if (amount == null) {
            errors.put("amount", "is required");
        } else if (amount.signum() <= 0) {
            errors.put("amount", "must be > 0");
        } else if (amount.compareTo(MAX_AMOUNT) > 0) {
            errors.put("amount", "must be ≤ ₹1,00,00,000");
        } else if (amount.stripTrailingZeros().scale() > 2) {
            errors.put("amount", "can have at most 2 decimal places");
        }

        String v = clean(vendor);
        if (v == null) {
            errors.put("vendor", "is required");
        } else if (v.length() > VENDOR_MAX) {
            errors.put("vendor", "must be at most " + VENDOR_MAX + " characters");
        }

        String d = clean(description);
        if (d != null && d.length() > DESCRIPTION_MAX) {
            errors.put("description", "must be at most " + DESCRIPTION_MAX + " characters");
        }
        return errors;
    }

    /** Trims; blank becomes null. */
    public static String clean(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
