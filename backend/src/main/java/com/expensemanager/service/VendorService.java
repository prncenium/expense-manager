package com.expensemanager.service;

import com.expensemanager.dto.UncategorizedVendor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Uncategorized vendors panel and the label list (SPEC A.5). */
@Service
public class VendorService {

    public static final int PANEL_LIMIT = 50;

    private final NamedParameterJdbcTemplate jdbc;
    private final CategorizationService categorizationService;

    public VendorService(NamedParameterJdbcTemplate jdbc, CategorizationService categorizationService) {
        this.jdbc = jdbc;
        this.categorizationService = categorizationService;
    }

    /** Vendors in Other with no label, grouped like top vendors, biggest total first. */
    @Transactional(readOnly = true)
    public List<UncategorizedVendor> uncategorized() {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("other", categorizationService.otherCategoryId())
                .addValue("limit", PANEL_LIMIT);
        return jdbc.query("""
                SELECT mode() WITHIN GROUP (ORDER BY trim(vendor)) AS vendor,
                       COUNT(*) AS cnt, SUM(amount) AS total, MAX(expense_date) AS last_date
                FROM expenses
                WHERE category_id = :other AND label IS NULL
                GROUP BY lower(trim(vendor))
                ORDER BY total DESC, vendor
                LIMIT :limit
                """, p, (rs, i) -> {
            String vendor = rs.getString("vendor");
            return new UncategorizedVendor(vendor, suggestKeyword(vendor), rs.getLong("cnt"),
                    rs.getBigDecimal("total"), rs.getObject("last_date", LocalDate.class));
        });
    }

    /** How many distinct vendors the panel would show (no limit), for the dashboard hint. */
    @Transactional(readOnly = true)
    public long uncategorizedCount() {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT lower(trim(vendor))) FROM expenses
                WHERE category_id = :other AND label IS NULL
                """, new MapSqlParameterSource("other", categorizationService.otherCategoryId()), Long.class);
        return n == null ? 0 : n;
    }

    /** Distinct labels used by expenses or rules, sorted case-insensitively. */
    @Transactional(readOnly = true)
    public List<String> labels() {
        return jdbc.queryForList("""
                SELECT label FROM (
                  SELECT DISTINCT ON (lower(label)) label
                  FROM (SELECT label FROM expenses WHERE label IS NOT NULL
                        UNION ALL
                        SELECT label FROM vendor_rules WHERE label IS NOT NULL) l
                  ORDER BY lower(label), label
                ) d ORDER BY lower(label)
                """, new MapSqlParameterSource(), String.class);
    }

    static String suggestKeyword(String vendor) {
        String k = vendor.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return k.length() > RuleService.KEYWORD_MAX ? k.substring(0, RuleService.KEYWORD_MAX).trim() : k;
    }
}
