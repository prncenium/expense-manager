package com.expensemanager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Body of POST /api/expenses. Validated by ExpenseValidator (same rules as CSV rows). */
public record ExpenseRequest(LocalDate date, BigDecimal amount, String vendor, String description) {
}
