package com.expensemanager.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** An expense as read through the anomaly query (SPEC §6). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExpenseView(
        Long id,
        LocalDate date,
        BigDecimal amount,
        String vendor,
        String description,
        Short categoryId,
        String category,
        /* nickname inside Other, else null */
        String label,
        String source,
        OffsetDateTime createdAt,
        BigDecimal categoryAvg,
        BigDecimal timesAvg,
        boolean anomaly,
        /* only set on POST /expenses */
        Boolean possibleDuplicate) {

    public ExpenseView withPossibleDuplicate(boolean duplicate) {
        return new ExpenseView(id, date, amount, vendor, description, categoryId, category, label, source,
                createdAt, categoryAvg, timesAvg, anomaly, duplicate);
    }
}
