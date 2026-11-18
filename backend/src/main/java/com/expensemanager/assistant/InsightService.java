package com.expensemanager.assistant;

import com.expensemanager.assistant.Insight.Type;
import com.expensemanager.dto.ExpenseView;
import com.expensemanager.service.ExpenseQueryRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Saving ideas for one month (ASSISTANT-SPEC §5). Plain rules on SQL aggregates, no AI, and
 * never loads all expenses into memory: every query returns a handful of grouped rows.
 */
@Service
public class InsightService {

    static final int MAX_INSIGHTS = 6;
    static final int MAX_HIGH_EXPENSES = 3;
    static final BigDecimal SPIKE_MIN_PCT = BigDecimal.valueOf(25);
    static final BigDecimal SPIKE_MIN_RUPEES = BigDecimal.valueOf(500);
    static final int FREQUENT_MIN_COUNT = 8;
    static final BigDecimal FREQUENT_MAX_AVG = BigDecimal.valueOf(500);
    static final BigDecimal SUBSCRIPTION_TOLERANCE = new BigDecimal("1.10");
    static final BigDecimal TOP_SHARE_MIN_PCT = BigDecimal.valueOf(40);
    static final BigDecimal WEEKEND_MIN_PCT = BigDecimal.valueOf(50);
    /** TOP_SHARE and WEEKEND are noise on a nearly empty month. */
    static final BigDecimal SHARE_MIN_MONTH_TOTAL = BigDecimal.valueOf(1000);

    private static final DateTimeFormatter MONTH_NAME = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final NamedParameterJdbcTemplate jdbc;
    private final ExpenseQueryRepository queryRepository;

    public InsightService(NamedParameterJdbcTemplate jdbc, ExpenseQueryRepository queryRepository) {
        this.jdbc = jdbc;
        this.queryRepository = queryRepository;
    }

    /** At most {@value #MAX_INSIGHTS} insights for {@code month}, biggest amount first. */
    @Transactional(readOnly = true)
    public List<Insight> insights(YearMonth month) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("prev2", month.minusMonths(2).atDay(1))
                .addValue("prev", month.minusMonths(1).atDay(1))
                .addValue("cur", month.atDay(1))
                .addValue("next", month.plusMonths(1).atDay(1));
        List<Insight> all = new ArrayList<>();
        spikes(month, p, all);
        frequentSmall(p, all);
        subscriptions(p, all);
        highExpenses(month, all);
        topShare(month, p, all);
        weekend(month, p, all);
        all.sort(Comparator.comparing(Insight::amount).reversed());
        return all.size() > MAX_INSIGHTS ? List.copyOf(all.subList(0, MAX_INSIGHTS)) : all;
    }

    /** Category (Other split by nickname) up ≥ 25 % and ≥ ₹500 vs the month before. */
    private void spikes(YearMonth month, MapSqlParameterSource p, List<Insight> out) {
        String cur = name(month);
        String prev = name(month.minusMonths(1));
        jdbc.query("""
                SELECT c.name AS category, e.label,
                       COALESCE(SUM(e.amount) FILTER (WHERE e.expense_date >= :cur), 0) AS current,
                       COALESCE(SUM(e.amount) FILTER (WHERE e.expense_date <  :cur), 0) AS previous
                FROM expenses e
                JOIN categories c ON c.id = e.category_id
                WHERE e.expense_date >= :prev AND e.expense_date < :next
                GROUP BY c.id, c.name, e.label
                """, p, rs -> {
            BigDecimal now = rs.getBigDecimal("current");
            BigDecimal before = rs.getBigDecimal("previous");
            BigDecimal diff = now.subtract(before);
            if (before.signum() == 0 || diff.compareTo(SPIKE_MIN_RUPEES) < 0
                    || diff.multiply(HUNDRED).compareTo(SPIKE_MIN_PCT.multiply(before)) < 0) {
                return;
            }
            String category = display(rs.getString("category"), rs.getString("label"));
            BigDecimal pct = diff.multiply(HUNDRED).divide(before, 0, RoundingMode.HALF_UP);
            out.add(Insight.of(Type.SPIKE,
                    category + " is up " + pct + "% from " + prev,
                    Rupees.format(now) + " in " + cur + " vs " + Rupees.format(before) + " in " + prev
                            + ". Going back to " + prev + "'s level saves " + Rupees.format(diff) + ".",
                    now, diff,
                    "Was the extra " + category + " spend a one-off, or will it continue?"));
        });
    }

    /** One vendor ≥ 8 times in the month with an average ≤ ₹500. Saving = half of its total. */
    private void frequentSmall(MapSqlParameterSource p, List<Insight> out) {
        p.addValue("minCount", FREQUENT_MIN_COUNT).addValue("maxAvg", FREQUENT_MAX_AVG);
        jdbc.query("""
                SELECT mode() WITHIN GROUP (ORDER BY trim(vendor)) AS vendor,
                       COUNT(*) AS cnt, SUM(amount) AS total
                FROM expenses
                WHERE expense_date >= :cur AND expense_date < :next
                GROUP BY lower(trim(vendor))
                HAVING COUNT(*) >= :minCount AND AVG(amount) <= :maxAvg
                """, p, rs -> {
            String vendor = rs.getString("vendor");
            long count = rs.getLong("cnt");
            BigDecimal total = rs.getBigDecimal("total");
            BigDecimal avg = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
            BigDecimal half = total.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            out.add(Insight.of(Type.FREQUENT_SMALL,
                    vendor + ": " + count + " small spends this month",
                    count + " times, average " + Rupees.format(avg) + ", " + Rupees.format(total)
                            + " in total. Half as many saves about " + Rupees.format(half) + ".",
                    total, half,
                    "What are most of the " + vendor + " spends for?"));
        });
    }

    /**
     * Same vendor charged exactly once in this month and once in the month before (and at most
     * once in the month before that), every charge within 10 % of the others. Saving = this
     * month's charge, if the user no longer needs it.
     */
    private void subscriptions(MapSqlParameterSource p, List<Insight> out) {
        p.addValue("tolerance", SUBSCRIPTION_TOLERANCE);
        jdbc.query("""
                SELECT mode() WITHIN GROUP (ORDER BY trim(vendor)) AS vendor,
                       COUNT(*) AS months,
                       SUM(amount) FILTER (WHERE expense_date >= :cur) AS current
                FROM expenses
                WHERE expense_date >= :prev2 AND expense_date < :next
                GROUP BY lower(trim(vendor))
                HAVING COUNT(*) = COUNT(DISTINCT date_trunc('month', expense_date))
                   AND COUNT(*) FILTER (WHERE expense_date >= :cur) = 1
                   AND COUNT(*) FILTER (WHERE expense_date >= :prev AND expense_date < :cur) = 1
                   AND MAX(amount) <= MIN(amount) * :tolerance
                """, p, rs -> {
            String vendor = rs.getString("vendor");
            BigDecimal current = rs.getBigDecimal("current");
            out.add(Insight.of(Type.SUBSCRIPTION,
                    vendor + ": about " + Rupees.format(current) + " every month",
                    "Charged once a month, a similar amount, in each of the last " + rs.getLong("months")
                            + " months. Stopping it saves " + Rupees.format(current) + " a month.",
                    current, current,
                    "Do you still use " + vendor + "?"));
        });
    }

    /** The month's anomalies (existing 3× rule, SPEC §6), biggest first, at most 3. */
    private void highExpenses(YearMonth month, List<Insight> out) {
        queryRepository.find(month, null, true, null, ExpenseQueryRepository.LIST_LIMIT).stream()
                .sorted(Comparator.comparing(ExpenseView::amount).reversed())
                .limit(MAX_HIGH_EXPENSES)
                .forEach(e -> {
                    String category = display(e.category(), e.label());
                    out.add(Insight.of(Type.HIGH_EXPENSE,
                            e.vendor() + " " + Rupees.format(e.amount()) + " on " + DAY.format(e.date()),
                            e.timesAvg().setScale(1, RoundingMode.HALF_UP) + "× your usual " + category
                                    + " expense (average " + Rupees.format(e.categoryAvg()) + ").",
                            e.amount(), null,
                            "Was the " + e.vendor() + " " + Rupees.format(e.amount()) + " a one-off?"));
                });
    }

    /** One category over 40 % of the month (month total ≥ ₹1,000). */
    private void topShare(YearMonth month, MapSqlParameterSource p, List<Insight> out) {
        p.addValue("minTotal", SHARE_MIN_MONTH_TOTAL);
        jdbc.query("""
                SELECT category, total, month_total FROM (
                    SELECT c.name AS category, SUM(e.amount) AS total,
                           SUM(SUM(e.amount)) OVER () AS month_total
                    FROM expenses e
                    JOIN categories c ON c.id = e.category_id
                    WHERE e.expense_date >= :cur AND e.expense_date < :next
                    GROUP BY c.id, c.name
                ) t
                WHERE month_total >= :minTotal
                """, p, rs -> {
            BigDecimal total = rs.getBigDecimal("total");
            BigDecimal monthTotal = rs.getBigDecimal("month_total");
            if (total.multiply(HUNDRED).compareTo(TOP_SHARE_MIN_PCT.multiply(monthTotal)) <= 0) {
                return;
            }
            BigDecimal pct = total.multiply(HUNDRED).divide(monthTotal, 0, RoundingMode.HALF_UP);
            out.add(Insight.of(Type.TOP_SHARE,
                    rs.getString("category") + " is " + pct + "% of " + name(month),
                    Rupees.format(total) + " of " + Rupees.format(monthTotal) + " spent this month.",
                    total, null, null));
        });
    }

    /** Fri–Sun is ≥ 50 % of the month's spend (month total ≥ ₹1,000). */
    private void weekend(YearMonth month, MapSqlParameterSource p, List<Insight> out) {
        jdbc.query("""
                SELECT COALESCE(SUM(amount) FILTER (WHERE EXTRACT(ISODOW FROM expense_date) >= 5), 0) AS weekend,
                       COALESCE(SUM(amount), 0) AS total
                FROM expenses
                WHERE expense_date >= :cur AND expense_date < :next
                """, p, rs -> {
            BigDecimal weekend = rs.getBigDecimal("weekend");
            BigDecimal total = rs.getBigDecimal("total");
            if (total.compareTo(SHARE_MIN_MONTH_TOTAL) < 0
                    || weekend.multiply(HUNDRED).compareTo(WEEKEND_MIN_PCT.multiply(total)) < 0) {
                return;
            }
            BigDecimal pct = weekend.multiply(HUNDRED).divide(total, 0, RoundingMode.HALF_UP);
            out.add(Insight.of(Type.WEEKEND,
                    pct + "% of " + name(month) + " spend is on Fri–Sun",
                    Rupees.format(weekend) + " of " + Rupees.format(total) + " was spent on Fridays to Sundays.",
                    weekend, null,
                    "What is most of the weekend spend on: eating out, shopping or going out?"));
        });
    }

    private static String name(YearMonth month) {
        return MONTH_NAME.format(month);
    }

    private static String display(String category, String label) {
        return label == null ? category : category + " · " + label;
    }
}
