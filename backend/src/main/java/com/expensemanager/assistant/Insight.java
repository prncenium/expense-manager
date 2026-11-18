package com.expensemanager.assistant;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * One saving idea (ASSISTANT-SPEC §5). Amounts come with a ready-made text ("₹4,200") so the
 * model quotes them instead of formatting numbers itself. {@code possibleSaving} follows a stated
 * rule (see {@link InsightService}), never a guess.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Insight(
        Type type,
        String title,
        String evidence,
        BigDecimal amount,
        String amountText,
        BigDecimal possibleSaving,
        String possibleSavingText,
        /* a cross question Muneem may ask about this insight, or null */
        String askUser) {

    public enum Type { SPIKE, FREQUENT_SMALL, SUBSCRIPTION, HIGH_EXPENSE, TOP_SHARE, WEEKEND }

    static Insight of(Type type, String title, String evidence, BigDecimal amount,
                      BigDecimal possibleSaving, String askUser) {
        return new Insight(type, title, evidence, amount, Rupees.format(amount),
                possibleSaving, possibleSaving == null ? null : Rupees.format(possibleSaving), askUser);
    }
}
