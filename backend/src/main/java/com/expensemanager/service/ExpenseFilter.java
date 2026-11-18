package com.expensemanager.service;

import com.expensemanager.exception.BadRequestException;

import java.time.YearMonth;
import java.util.Locale;

/**
 * Filters shared by the list, the export and the dashboard (SPEC C/D). Every field may be null.
 * label only narrows when categoryId is also given (the UI always sends both).
 */
public record ExpenseFilter(YearMonth month, Short categoryId, String label, boolean anomaliesOnly, String q) {

    public static final int Q_MAX = 100;

    public static final ExpenseFilter NONE = new ExpenseFilter(null, null, null, false, null);

    public static ExpenseFilter of(String month, Short categoryId, String label, boolean anomaliesOnly, String q) {
        String search = q == null ? null : q.trim();
        if (search != null && search.length() > Q_MAX) {
            throw new BadRequestException("q must be at most " + Q_MAX + " characters");
        }
        String l = label == null || label.isBlank() ? null : label.trim();
        return new ExpenseFilter(Months.parse(month), categoryId, l, anomaliesOnly,
                search == null || search.isEmpty() ? null : search);
    }

    /** ILIKE pattern with %, _ and \ taken literally. */
    String likePattern() {
        String escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped.toLowerCase(Locale.ROOT) + "%";
    }
}
