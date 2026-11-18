package com.expensemanager.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** recategorized is only set on create / update: how many Other expenses moved (SPEC A.4). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuleDto(Long id, String keyword, Short categoryId, String category, String label,
                      Integer recategorized) {
}
