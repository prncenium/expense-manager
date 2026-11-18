package com.expensemanager.dto;

import java.math.BigDecimal;
import java.util.List;

/** One page of GET /api/expenses (SPEC C). Totals cover every matching row, not just this page. */
public record ExpensePage(List<ExpenseView> items, int page, int size, long totalItems, int totalPages,
                          BigDecimal totalAmount) {
}
