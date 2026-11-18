package com.expensemanager.assistant;

import com.expensemanager.assistant.llm.ToolDef;
import com.expensemanager.dto.CompareResponse;
import com.expensemanager.dto.ExpenseView;
import com.expensemanager.entity.Category;
import com.expensemanager.exception.BadRequestException;
import com.expensemanager.exception.ValidationException;
import com.expensemanager.repository.CategoryRepository;
import com.expensemanager.service.CompareService;
import com.expensemanager.service.ExpenseFilter;
import com.expensemanager.service.ExpenseQueryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tools Muneem may call (ASSISTANT-SPEC §4). All read-only except {@code save_note}. Every
 * result is small (≤ 20 rows) and built from SQL aggregates; amounts are sent as ready text
 * ("₹4,200") so the model never formats or computes them. Bad arguments come back as
 * {@code {"error": "..."}} for the model to fix, never as an HTTP error.
 */
@Component
public class AssistantTools {

    static final int FIND_MAX = 20;
    static final int FIND_DEFAULT = 10;
    static final int VENDORS_MAX = 10;
    static final int VENDORS_DEFAULT = 5;

    private static final Map<String, Object> DATE = Map.of("type", "string", "description", "YYYY-MM-DD");
    private static final Map<String, Object> MONTH = Map.of("type", "string", "description", "YYYY-MM");

    static final List<ToolDef> DEFS = List.of(
            ToolDef.of("get_spend_summary",
                    "Total spend and spend per category between two dates (inclusive, at most 366 days).",
                    object(Map.of("from", DATE, "to", DATE), "from", "to")),
            ToolDef.of("compare_periods",
                    "Spend per category in period A vs period B (A is the baseline). Use for this month vs last month etc.",
                    object(Map.of("aFrom", DATE, "aTo", DATE, "bFrom", DATE, "bTo", DATE), "aFrom", "aTo", "bFrom", "bTo")),
            ToolDef.of("top_vendors",
                    "Vendors with the highest total spend between two dates.",
                    object(Map.of("from", DATE, "to", DATE,
                            "limit", Map.of("type", "integer", "description", "1-10, default 5")), "from", "to")),
            ToolDef.of("find_expenses",
                    "Individual expenses, newest first, plus the count and total of all matches. "
                            + "Use anomaliesOnly for unusually high expenses.",
                    object(Map.of(
                            "month", MONTH,
                            "category", Map.of("type", "string", "description", "category name, e.g. Food"),
                            "label", Map.of("type", "string", "description", "nickname inside Other, e.g. Gym"),
                            "search", Map.of("type", "string", "description", "text in vendor or description"),
                            "anomaliesOnly", Map.of("type", "boolean"),
                            "limit", Map.of("type", "integer", "description", "1-20, default 10")))),
            ToolDef.of("get_saving_insights",
                    "Ready-made saving ideas for one month: spikes, frequent small spends, subscriptions, "
                            + "high expenses, big category share, weekend spend. Each may carry askUser, a good follow-up question.",
                    object(Map.of("month", MONTH), "month")),
            ToolDef.of("save_note",
                    "Remember one short lasting fact the user told you (max 200 characters), for future chats.",
                    object(Map.of("note", Map.of("type", "string")), "note")));

    private final CompareService compareService;
    private final ExpenseQueryRepository queryRepository;
    private final InsightService insightService;
    private final NoteService noteService;
    private final CategoryRepository categoryRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public AssistantTools(CompareService compareService, ExpenseQueryRepository queryRepository,
                          InsightService insightService, NoteService noteService,
                          CategoryRepository categoryRepository, NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.compareService = compareService;
        this.queryRepository = queryRepository;
        this.insightService = insightService;
        this.noteService = noteService;
        this.categoryRepository = categoryRepository;
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<ToolDef> definitions() {
        return DEFS;
    }

    /** Runs one tool call; always returns a JSON string (a result or {"error": ...}). */
    public String call(String name, String arguments) {
        Object result;
        try {
            JsonNode args = arguments == null || arguments.isBlank() ? json.createObjectNode() : json.readTree(arguments);
            result = switch (name == null ? "" : name) {
                case "get_spend_summary" -> spendSummary(text(args, "from"), text(args, "to"));
                case "compare_periods" -> compare(text(args, "aFrom"), text(args, "aTo"),
                        text(args, "bFrom"), text(args, "bTo"));
                case "top_vendors" -> topVendors(text(args, "from"), text(args, "to"),
                        limit(args, VENDORS_DEFAULT, VENDORS_MAX));
                case "find_expenses" -> findExpenses(args);
                case "get_saving_insights" -> Map.of("insights", insightService.insights(requiredMonth(text(args, "month"))));
                case "save_note" -> Map.of("saved", noteService.save(text(args, "note")));
                default -> Map.of("error", "unknown tool " + name);
            };
        } catch (BadRequestException | ValidationException | IllegalArgumentException e) {
            result = Map.of("error", e.getMessage());
        } catch (JsonProcessingException e) {
            result = Map.of("error", "arguments must be a JSON object");
        }
        try {
            return json.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> spendSummary(String from, String to) {
        CompareResponse r = compareService.compare(from, to, from, to);
        List<Map<String, Object>> categories = r.rows().stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", display(row.category(), row.label()));
            m.put("amount", Rupees.format(row.b()));
            m.put("sharePct", pct(row.b(), r.total().b()));
            return m;
        }).toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", r.b().from().toString());
        m.put("to", r.b().to().toString());
        m.put("expenses", r.b().count());
        m.put("total", Rupees.format(r.total().b()));
        m.put("categories", categories);
        return m;
    }

    private Map<String, Object> compare(String aFrom, String aTo, String bFrom, String bTo) {
        CompareResponse r = compareService.compare(aFrom, aTo, bFrom, bTo);
        List<Map<String, Object>> rows = r.rows().stream().map(row -> compareRow(
                display(row.category(), row.label()), row)).toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("a", Map.of("from", r.a().from().toString(), "to", r.a().to().toString(),
                "days", r.a().days(), "expenses", r.a().count()));
        m.put("b", Map.of("from", r.b().from().toString(), "to", r.b().to().toString(),
                "days", r.b().days(), "expenses", r.b().count()));
        m.put("total", compareRow(null, r.total()));
        m.put("categories", rows);
        return m;
    }

    private static Map<String, Object> compareRow(String category, CompareResponse.Row row) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (category != null) {
            m.put("category", category);
        }
        m.put("a", Rupees.format(row.a()));
        m.put("b", Rupees.format(row.b()));
        m.put("change", Rupees.format(row.b().subtract(row.a())));
        m.put("changePct", row.changePct() == null ? "new" : row.changePct().toPlainString() + "%");
        return m;
    }

    private Map<String, Object> topVendors(String from, String to, int limit) {
        // validates both dates and the 366-day cap the same way as the Compare page
        CompareResponse.Period period = compareService.compare(from, to, from, to).b();
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", period.from()).addValue("end", period.to().plusDays(1)).addValue("limit", limit);
        List<Map<String, Object>> vendors = jdbc.query("""
                SELECT mode() WITHIN GROUP (ORDER BY trim(vendor)) AS vendor,
                       SUM(amount) AS total, COUNT(*) AS cnt
                FROM expenses
                WHERE expense_date >= :from AND expense_date < :end
                GROUP BY lower(trim(vendor))
                ORDER BY total DESC, vendor
                LIMIT :limit
                """, p, (rs, i) -> Map.of(
                "vendor", rs.getString("vendor"),
                "total", Rupees.format(rs.getBigDecimal("total")),
                "times", rs.getLong("cnt")));
        return Map.of("from", period.from().toString(), "to", period.to().toString(), "vendors", vendors);
    }

    private Map<String, Object> findExpenses(JsonNode args) {
        Short categoryId = null;
        String categoryName = text(args, "category");
        if (categoryName != null && !categoryName.isBlank()) {
            categoryId = categoryRepository.findAll().stream()
                    .filter(c -> c.getName().equalsIgnoreCase(categoryName.trim()))
                    .map(Category::getId).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown category " + categoryName));
        }
        String month = text(args, "month");
        month(month);   // same error text as the other tools
        ExpenseFilter filter = ExpenseFilter.of(month, categoryId, text(args, "label"),
                args.path("anomaliesOnly").asBoolean(false), text(args, "search"));
        int limit = limit(args, FIND_DEFAULT, FIND_MAX);
        List<Map<String, Object>> rows = queryRepository.find(filter, null, limit, 0).stream()
                .map(AssistantTools::expenseRow).toList();
        ExpenseQueryRepository.Totals totals = queryRepository.totals(filter);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("matches", totals.count());
        m.put("total", Rupees.format(totals.amount()));
        m.put("shown", rows.size());
        m.put("expenses", rows);
        return m;
    }

    private static Map<String, Object> expenseRow(ExpenseView e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", e.date().toString());
        m.put("vendor", e.vendor());
        m.put("amount", Rupees.format(e.amount()));
        m.put("category", display(e.category(), e.label()));
        if (e.description() != null && !e.description().isBlank()) {
            m.put("description", e.description());
        }
        if (e.anomaly()) {
            m.put("high", e.timesAvg().setScale(1, RoundingMode.HALF_UP) + "× category average");
        }
        return m;
    }

    private static YearMonth requiredMonth(String value) {
        YearMonth month = month(value);
        if (month == null) {
            throw new IllegalArgumentException("month is required (YYYY-MM, e.g. 2026-11)");
        }
        return month;
    }

    private static YearMonth month(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("month must be YYYY-MM, e.g. 2026-11");
        }
    }

    private static String text(JsonNode args, String field) {
        JsonNode v = args.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static int limit(JsonNode args, int byDefault, int max) {
        int n = args.path("limit").asInt(byDefault);
        return Math.max(1, Math.min(max, n));
    }

    private static String pct(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return "0%";
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 0, RoundingMode.HALF_UP) + "%";
    }

    private static String display(String category, String label) {
        return label == null ? category : category + " · " + label;
    }

    private static Map<String, Object> object(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required.length > 0) {
            schema.put("required", List.of(required));
        }
        return schema;
    }
}
