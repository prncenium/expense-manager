package com.expensemanager.service;

import com.expensemanager.exception.BadRequestException;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

final class Months {

    private Months() {
    }

    /** "2026-11" → YearMonth; null/blank → null; anything else → 400. */
    static YearMonth parse(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException("month must be YYYY-MM, e.g. 2026-11");
        }
    }
}
