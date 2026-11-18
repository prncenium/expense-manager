package com.expensemanager.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(
        List<String> months,
        List<MonthlyTotal> monthlyTotals,
        List<TopVendor> topVendors,
        Anomalies anomalies,
        Totals totals,
        MonthOverMonth monthOverMonth,
        long uncategorizedVendors) {

    /** month is "YYYY-MM". */
    public record MonthlyTotal(String month, Short categoryId, String category, BigDecimal total) {
    }

    public record TopVendor(String vendor, BigDecimal total, long count) {
    }

    public record Anomalies(int count, List<ExpenseView> items) {
    }

    public record Totals(BigDecimal spend, long count) {
    }

    /** SPEC B. month / previousMonth are "YYYY-MM"; changePct is null when previous is 0. */
    public record MonthOverMonth(String month, String previousMonth, MomRow total, List<MomRow> rows) {
    }

    /** categoryId / category / label are null on the total row. */
    public record MomRow(Short categoryId, String category, String label, BigDecimal current,
                         BigDecimal previous, BigDecimal changePct) {
    }
}
