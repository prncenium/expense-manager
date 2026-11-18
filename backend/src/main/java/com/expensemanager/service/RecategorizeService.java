package com.expensemanager.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Re-applies the rules to expenses in Other (SPEC A.4). Expenses in a real category are never
 * touched, and an expense only moves when a rule matches it.
 */
@Service
public class RecategorizeService {

    private final NamedParameterJdbcTemplate jdbc;
    private final CategorizationService categorizationService;

    public RecategorizeService(NamedParameterJdbcTemplate jdbc, CategorizationService categorizationService) {
        this.jdbc = jdbc;
        this.categorizationService = categorizationService;
    }

    /** Returns how many expenses changed. */
    @Transactional
    public int reapplyToOther() {
        Short otherId = categorizationService.otherCategoryId();
        List<SqlParameterSource> updates = new ArrayList<>();
        jdbc.query("SELECT id, vendor, label FROM expenses WHERE category_id = :other",
                new MapSqlParameterSource("other", otherId), rs -> {
                    CategorizationService.Match m = categorizationService.match(rs.getString("vendor"));
                    if (m.matched() && (!m.categoryId().equals(otherId)
                            || !Objects.equals(m.label(), rs.getString("label")))) {
                        updates.add(new MapSqlParameterSource()
                                .addValue("id", rs.getLong("id"))
                                .addValue("cat", m.categoryId())
                                .addValue("label", m.label()));
                    }
                });
        if (!updates.isEmpty()) {
            jdbc.batchUpdate("UPDATE expenses SET category_id = :cat, label = :label WHERE id = :id",
                    updates.toArray(SqlParameterSource[]::new));
        }
        return updates.size();
    }
}
