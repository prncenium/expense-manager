package com.expensemanager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** SPEC F. Period A is the baseline, B is compared against it; changePct = (b - a) / a, null when a is 0. */
public record CompareResponse(Period a, Period b, Row total, List<Row> rows) {

    /** from / to are inclusive; days = number of calendar days in the range. */
    public record Period(LocalDate from, LocalDate to, long days, long count) {
    }

    /** categoryId / category / label are null on the total row. */
    public record Row(Short categoryId, String category, String label, BigDecimal a, BigDecimal b,
                      BigDecimal changePct) {
    }
}
