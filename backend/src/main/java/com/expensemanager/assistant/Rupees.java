package com.expensemanager.assistant;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Formats amounts the Indian way ("₹1,24,500", "₹233.50") so the model can quote them as-is
 * and never formats a number itself (ASSISTANT-SPEC §3.2).
 */
public final class Rupees {

    private Rupees() {
    }

    public static String format(BigDecimal amount) {
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        String sign = value.signum() < 0 ? "-" : "";
        value = value.abs();
        String whole = value.toBigInteger().toString();
        int paise = value.remainder(BigDecimal.ONE).movePointRight(2).intValue();

        StringBuilder grouped = new StringBuilder();
        int len = whole.length();
        if (len <= 3) {
            grouped.append(whole);
        } else {
            String head = whole.substring(0, len - 3);
            // lakh / crore grouping: pairs before the last three digits
            int first = head.length() % 2 == 0 ? 2 : 1;
            grouped.append(head, 0, first);
            for (int i = first; i < head.length(); i += 2) {
                grouped.append(',').append(head, i, i + 2);
            }
            grouped.append(',').append(whole.substring(len - 3));
        }
        String text = sign + "₹" + grouped;
        return paise == 0 ? text : text + "." + String.format("%02d", paise);
    }
}
