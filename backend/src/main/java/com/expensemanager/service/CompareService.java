package com.expensemanager.service;

import com.expensemanager.dto.CompareResponse;
import com.expensemanager.dto.CompareResponse.Period;
import com.expensemanager.dto.CompareResponse.Row;
import com.expensemanager.exception.BadRequestException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * SPEC F: compares spend per category between two date ranges (any length, may overlap).
 * The UI turns "month vs month" and "week vs week" into ranges, so one endpoint serves all modes.
 */
@Service
public class CompareService {

    static final long MAX_DAYS = 366;

    private final NamedParameterJdbcTemplate jdbc;

    public CompareService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CompareResponse compare(String aFrom, String aTo, String bFrom, String bTo) {
        LocalDate a1 = date("aFrom", aFrom), a2 = date("aTo", aTo);
        LocalDate b1 = date("bFrom", bFrom), b2 = date("bTo", bTo);
        long aDays = days("A", a1, a2), bDays = days("B", b1, b2);

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("aFrom", a1).addValue("aEnd", a2.plusDays(1))
                .addValue("bFrom", b1).addValue("bEnd", b2.plusDays(1));
        List<Row> rows = new ArrayList<>();
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        Row[] total = {new Row(null, null, null, zero, zero, null)};
        long[] counts = {0, 0};
        // One pass with FILTER aggregates (ranges may overlap); GROUPING SETS adds the grand-total row.
        jdbc.query("""
                SELECT c.id AS category_id, c.name AS category, e.label,
                       GROUPING(c.id) AS is_total,
                       COALESCE(SUM(e.amount) FILTER (WHERE e.expense_date >= :aFrom AND e.expense_date < :aEnd), 0) AS a_sum,
                       COALESCE(SUM(e.amount) FILTER (WHERE e.expense_date >= :bFrom AND e.expense_date < :bEnd), 0) AS b_sum,
                       COUNT(*) FILTER (WHERE e.expense_date >= :aFrom AND e.expense_date < :aEnd) AS a_cnt,
                       COUNT(*) FILTER (WHERE e.expense_date >= :bFrom AND e.expense_date < :bEnd) AS b_cnt
                FROM expenses e
                JOIN categories c ON c.id = e.category_id
                WHERE (e.expense_date >= :aFrom AND e.expense_date < :aEnd)
                   OR (e.expense_date >= :bFrom AND e.expense_date < :bEnd)
                GROUP BY GROUPING SETS ((c.id, c.name, e.label), ())
                ORDER BY is_total, b_sum DESC, a_sum DESC, c.id, e.label
                """, p, rs -> {
            BigDecimal a = rs.getBigDecimal("a_sum").setScale(2);
            BigDecimal b = rs.getBigDecimal("b_sum").setScale(2);
            if (rs.getInt("is_total") == 1) {
                total[0] = new Row(null, null, null, a, b, DashboardService.changePct(b, a));
                counts[0] = rs.getLong("a_cnt");
                counts[1] = rs.getLong("b_cnt");
            } else {
                rows.add(new Row(rs.getShort("category_id"), rs.getString("category"), rs.getString("label"),
                        a, b, DashboardService.changePct(b, a)));
            }
        });
        return new CompareResponse(
                new Period(a1, a2, aDays, counts[0]),
                new Period(b1, b2, bDays, counts[1]),
                total[0], rows);
    }

    private static LocalDate date(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(name + " is required (YYYY-MM-DD)");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException(name + " must be YYYY-MM-DD, e.g. 2026-11-01");
        }
    }

    private static long days(String period, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BadRequestException("Period " + period + ": end date is before start date");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_DAYS) {
            throw new BadRequestException("Period " + period + " is longer than " + MAX_DAYS + " days");
        }
        return days;
    }
}
