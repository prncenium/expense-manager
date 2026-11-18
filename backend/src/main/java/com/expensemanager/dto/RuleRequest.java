package com.expensemanager.dto;

/** label = optional nickname, only allowed with category Other (SPEC A.2). */
public record RuleRequest(String keyword, Short categoryId, String label) {
}
