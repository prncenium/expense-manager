package com.expensemanager.service;

import com.expensemanager.dto.ExpenseView;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;

/**
 * Reads expenses with their live anomaly info (SPEC §6). The category average is computed in a
 * CTE over ALL expenses, before any filter, so filtering never changes the average or the flags.
 */
@Repository
public class ExpenseQueryRepository {

    public static final int LIST_LIMIT = 500;

    private static final String BASE = """
            WITH cat_avg AS (
              SELECT category_id, AVG(amount) AS category_avg
              FROM expenses GROUP BY category_id
            )
            SELECT e.id, e.expense_date, e.amount, e.vendor, e.description, e.category_id,
                   c.name AS category, e.label, e.source, e.created_at,
                   ROUND(a.category_avg, 2)            AS category_avg,
                   ROUND(e.amount / a.category_avg, 2) AS times_avg,
                   e.amount > 3 * a.category_avg       AS is_anomaly
            FROM expenses e
            JOIN categories c ON c.id = e.category_id
            JOIN cat_avg   a ON a.category_id = e.category_id
            WHERE TRUE
            """;

    private static final RowMapper<ExpenseView> MAPPER = (rs, i) -> new ExpenseView(
            rs.getLong("id"),
            rs.getObject("expense_date", java.time.LocalDate.class),
            rs.getBigDecimal("amount"),
            rs.getString("vendor"),
            rs.getString("description"),
            rs.getShort("category_id"),
            rs.getString("category"),
            rs.getString("label"),
            rs.getString("source"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getBigDecimal("category_avg"),
            rs.getBigDecimal("times_avg"),
            rs.getBoolean("is_anomaly"),
            null);

    private final NamedParameterJdbcTemplate jdbc;

    public ExpenseQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Filters apply only to the outer query; any argument may be null. Newest first. */
    public List<ExpenseView> find(YearMonth month, Short categoryId, boolean anomaliesOnly,
                                  Collection<Long> ids, int limit) {
        if (ids != null && ids.isEmpty()) {
            return List.of();
        }
        return find(new ExpenseFilter(month, categoryId, null, anomaliesOnly, null), ids, limit, 0);
    }

    /** One page, newest first. */
    public List<ExpenseView> find(ExpenseFilter f, Collection<Long> ids, int limit, long offset) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder(BASE).append(where(f, ids, p));
        sql.append(" ORDER BY e.expense_date DESC, e.id DESC LIMIT :limit OFFSET :offset");
        p.addValue("limit", limit);
        p.addValue("offset", offset);
        return jdbc.query(sql.toString(), p, MAPPER);
    }

    /** Streams every matching row (newest first, at most maxRows) to the handler, for the CSV export. */
    public void stream(ExpenseFilter f, int maxRows, java.util.function.Consumer<ExpenseView> handler) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = BASE + where(f, null, p) + " ORDER BY e.expense_date DESC, e.id DESC LIMIT :limit";
        p.addValue("limit", maxRows);
        jdbc.query(sql, p, rs -> {
            handler.accept(MAPPER.mapRow(rs, rs.getRow()));
        });
    }

    /** COUNT and SUM over every row the filter matches (same WHERE as {@link #find}). */
    public Totals totals(ExpenseFilter f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = """
                WITH cat_avg AS (
                  SELECT category_id, AVG(amount) AS category_avg
                  FROM expenses GROUP BY category_id
                )
                SELECT COUNT(*) AS cnt, COALESCE(SUM(e.amount), 0) AS total
                FROM expenses e
                JOIN cat_avg a ON a.category_id = e.category_id
                WHERE TRUE
                """ + where(f, null, p);
        return jdbc.queryForObject(sql, p, (rs, i) -> new Totals(rs.getLong("cnt"),
                rs.getBigDecimal("total").setScale(2, java.math.RoundingMode.UNNECESSARY)));
    }

    public record Totals(long count, java.math.BigDecimal amount) {
    }

    private static String where(ExpenseFilter f, Collection<Long> ids, MapSqlParameterSource p) {
        StringBuilder sql = new StringBuilder();
        if (f.month() != null) {
            sql.append(" AND e.expense_date >= :monthStart AND e.expense_date < :monthEnd");
            p.addValue("monthStart", f.month().atDay(1));
            p.addValue("monthEnd", f.month().plusMonths(1).atDay(1));
        }
        if (f.categoryId() != null) {
            sql.append(" AND e.category_id = :categoryId");
            p.addValue("categoryId", f.categoryId());
            if (f.label() != null) {
                sql.append(" AND lower(e.label) = lower(:label)");
                p.addValue("label", f.label());
            }
        }
        if (f.anomaliesOnly()) {
            sql.append(" AND e.amount > 3 * a.category_avg");
        }
        if (f.q() != null) {
            sql.append(" AND (lower(e.vendor) LIKE :q ESCAPE '\\' OR lower(COALESCE(e.description, '')) LIKE :q ESCAPE '\\')");
            p.addValue("q", f.likePattern());
        }
        if (ids != null) {
            sql.append(" AND e.id IN (:ids)");
            p.addValue("ids", ids);
        }
        return sql.toString();
    }

    public ExpenseView findById(long id) {
        List<ExpenseView> rows = find(null, null, false, List.of(id), 1);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** How many of these expenses are currently flagged as anomalies. */
    public int countAnomalies(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        Integer n = jdbc.queryForObject("""
                WITH cat_avg AS (
                  SELECT category_id, AVG(amount) AS category_avg
                  FROM expenses GROUP BY category_id
                )
                SELECT COUNT(*) FROM expenses e
                JOIN cat_avg a ON a.category_id = e.category_id
                WHERE e.id IN (:ids) AND e.amount > 3 * a.category_avg
                """, new MapSqlParameterSource("ids", ids), Integer.class);
        return n == null ? 0 : n;
    }
}
