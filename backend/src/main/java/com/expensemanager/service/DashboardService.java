package com.expensemanager.service;

import com.expensemanager.dto.DashboardResponse;
import com.expensemanager.dto.DashboardResponse.Anomalies;
import com.expensemanager.dto.DashboardResponse.MomRow;
import com.expensemanager.dto.DashboardResponse.MonthOverMonth;
import com.expensemanager.dto.DashboardResponse.MonthlyTotal;
import com.expensemanager.dto.DashboardResponse.TopVendor;
import com.expensemanager.dto.DashboardResponse.Totals;
import com.expensemanager.dto.ExpenseView;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Dashboard (SPEC §9). Monthly totals always cover the last 6 months up to the current month;
 * top vendors, anomalies and totals follow the optional month filter (default: all time).
 */
@Service
public class DashboardService {

    static final int MONTHS = 6;
    static final int TOP_VENDORS = 5;

    private final NamedParameterJdbcTemplate jdbc;
    private final ExpenseQueryRepository queryRepository;
    private final Clock clock;
    private final VendorService vendorService;

    public DashboardService(NamedParameterJdbcTemplate jdbc, ExpenseQueryRepository queryRepository, Clock clock,
                            VendorService vendorService) {
        this.jdbc = jdbc;
        this.queryRepository = queryRepository;
        this.clock = clock;
        this.vendorService = vendorService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse get(String monthParam) {
        YearMonth month = Months.parse(monthParam);
        List<ExpenseView> anomalies = queryRepository.find(month, null, true, null, ExpenseQueryRepository.LIST_LIMIT);
        return new DashboardResponse(
                lastMonths(),
                monthlyTotals(),
                topVendors(month),
                new Anomalies(anomalies.size(), anomalies),
                totals(month),
                monthOverMonth(month == null ? YearMonth.now(clock) : month),
                vendorService.uncategorizedCount());
    }

    /**
     * SPEC B: one pass with FILTER aggregates; GROUPING SETS adds the grand-total row
     * (GROUPING(c.id) = 1 marks it). Other is split by label.
     */
    MonthOverMonth monthOverMonth(YearMonth month) {
        YearMonth prev = month.minusMonths(1);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("prev", prev.atDay(1))
                .addValue("cur", month.atDay(1))
                .addValue("next", month.plusMonths(1).atDay(1));
        List<MomRow> rows = new ArrayList<>();
        MomRow[] total = {new MomRow(null, null, null, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), null)};
        jdbc.query("""
                SELECT c.id AS category_id, c.name AS category, e.label,
                       GROUPING(c.id) AS is_total,
                       COALESCE(SUM(e.amount) FILTER (WHERE e.expense_date >= :cur), 0) AS current,
                       COALESCE(SUM(e.amount) FILTER (WHERE e.expense_date <  :cur), 0) AS previous
                FROM expenses e
                JOIN categories c ON c.id = e.category_id
                WHERE e.expense_date >= :prev AND e.expense_date < :next
                GROUP BY GROUPING SETS ((c.id, c.name, e.label), ())
                ORDER BY is_total, current DESC, previous DESC, c.id, e.label
                """, p, rs -> {
            BigDecimal cur = rs.getBigDecimal("current").setScale(2);
            BigDecimal before = rs.getBigDecimal("previous").setScale(2);
            if (rs.getInt("is_total") == 1) {
                total[0] = new MomRow(null, null, null, cur, before, changePct(cur, before));
            } else {
                rows.add(new MomRow(rs.getShort("category_id"), rs.getString("category"), rs.getString("label"),
                        cur, before, changePct(cur, before)));
            }
        });
        return new MonthOverMonth(month.toString(), prev.toString(), total[0], rows);
    }

    /** (current - previous) / previous * 100, 1 decimal; null when previous is 0. */
    static BigDecimal changePct(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, java.math.RoundingMode.HALF_UP);
    }

    private List<String> lastMonths() {
        YearMonth current = YearMonth.now(clock);
        List<String> months = new ArrayList<>();
        for (int i = MONTHS - 1; i >= 0; i--) {
            months.add(current.minusMonths(i).toString());
        }
        return months;
    }

    private List<MonthlyTotal> monthlyTotals() {
        YearMonth current = YearMonth.now(clock);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", current.minusMonths(MONTHS - 1).atDay(1))
                .addValue("to", current.plusMonths(1).atDay(1));
        return jdbc.query("""
                SELECT to_char(date_trunc('month', e.expense_date), 'YYYY-MM') AS month,
                       c.id AS category_id, c.name AS category, SUM(e.amount) AS total
                FROM expenses e
                JOIN categories c ON c.id = e.category_id
                WHERE e.expense_date >= :from AND e.expense_date < :to
                GROUP BY 1, c.id, c.name
                ORDER BY 1, c.id
                """, p, (rs, i) -> new MonthlyTotal(
                rs.getString("month"), rs.getShort("category_id"), rs.getString("category"),
                rs.getBigDecimal("total")));
    }

    private List<TopVendor> topVendors(YearMonth month) {
        MapSqlParameterSource p = new MapSqlParameterSource("limit", TOP_VENDORS);
        // display name = the most used spelling in the group; mode() takes the first in sort order on a tie
        String sql = """
                SELECT mode() WITHIN GROUP (ORDER BY trim(vendor)) AS vendor,
                       SUM(amount) AS total, COUNT(*) AS cnt
                FROM expenses
                WHERE TRUE %s
                GROUP BY lower(trim(vendor))
                ORDER BY total DESC, vendor
                LIMIT :limit
                """.formatted(monthFilter(month, p));
        return jdbc.query(sql, p, (rs, i) -> new TopVendor(
                rs.getString("vendor"), rs.getBigDecimal("total"), rs.getLong("cnt")));
    }

    private Totals totals(YearMonth month) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = "SELECT COALESCE(SUM(amount), 0) AS spend, COUNT(*) AS cnt FROM expenses WHERE TRUE "
                + monthFilter(month, p);
        return jdbc.queryForObject(sql, p, (rs, i) -> new Totals(
                rs.getBigDecimal("spend").setScale(2), rs.getLong("cnt")));
    }

    private static String monthFilter(YearMonth month, MapSqlParameterSource p) {
        if (month == null) {
            return "";
        }
        p.addValue("monthStart", month.atDay(1));
        p.addValue("monthEnd", month.plusMonths(1).atDay(1));
        return " AND expense_date >= :monthStart AND expense_date < :monthEnd";
    }
}
