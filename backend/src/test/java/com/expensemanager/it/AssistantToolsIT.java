package com.expensemanager.it;

import com.expensemanager.assistant.AssistantTools;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.StringJoiner;

import static org.assertj.core.api.Assertions.assertThat;

/** ASSISTANT-SPEC §4: tools give the same numbers as the app's pages; bad args come back as {error}. */
class AssistantToolsIT extends AbstractIT {

    @Autowired
    private AssistantTools tools;

    @BeforeEach
    void data() throws Exception {
        upload("""
                date,amount,vendor,description
                2026-09-07,400,Swiggy,
                2026-10-05,100,Swiggy,office lunch
                2026-10-06,150,swiggy,
                2026-10-07,250,Zomato,
                2026-10-08,3000,Zomato,party
                2026-10-09,500,Uber,
                2026-10-12,900,Cult.fit,
                """).andReturn();
    }

    private JsonNode call(String name, String args) throws Exception {
        return json.readTree(tools.call(name, args));
    }

    @Test
    void spendSummaryMatchesTheComparePage() throws Exception {
        JsonNode r = call("get_spend_summary", "{\"from\":\"2026-10-01\",\"to\":\"2026-10-31\"}");
        JsonNode page = getJson("/api/compare?aFrom=2026-10-01&aTo=2026-10-31&bFrom=2026-10-01&bTo=2026-10-31");
        assertThat(page.at("/total/b").decimalValue()).isEqualByComparingTo("4900");
        assertThat(r.get("total").asText()).isEqualTo("₹4,900");
        assertThat(r.get("expenses").asLong()).isEqualTo(6);
        assertThat(r.at("/categories/0/category").asText()).isEqualTo("Food");
        assertThat(r.at("/categories/0/amount").asText()).isEqualTo("₹3,500");
        assertThat(r.at("/categories/0/sharePct").asText()).isEqualTo("71%");
    }

    @Test
    void comparePeriodsGivesTextAndChange() throws Exception {
        JsonNode r = call("compare_periods",
                "{\"aFrom\":\"2026-09-01\",\"aTo\":\"2026-09-30\",\"bFrom\":\"2026-10-01\",\"bTo\":\"2026-10-31\"}");
        assertThat(r.at("/total/a").asText()).isEqualTo("₹400");
        assertThat(r.at("/total/b").asText()).isEqualTo("₹4,900");
        assertThat(r.at("/total/change").asText()).isEqualTo("₹4,500");
        assertThat(r.at("/total/changePct").asText()).isEqualTo("1125.0%");
        assertThat(r.at("/categories/1/changePct").asText()).isEqualTo("new");
    }

    @Test
    void topVendorsGroupsSpellingsAndCapsTheLimit() throws Exception {
        JsonNode r = call("top_vendors", "{\"from\":\"2026-10-01\",\"to\":\"2026-10-31\",\"limit\":50}");
        assertThat(r.get("vendors")).hasSize(4);   // Zomato, Cult.fit, Uber, Swiggy (2 spellings, one group)
        assertThat(r.at("/vendors/0/vendor").asText()).isEqualTo("Zomato");
        assertThat(r.at("/vendors/0/total").asText()).isEqualTo("₹3,250");
        assertThat(r.at("/vendors/3/times").asLong()).isEqualTo(2);
    }

    @Test
    void findExpensesFiltersAndReportsTotals() throws Exception {
        JsonNode food = call("find_expenses", "{\"month\":\"2026-10\",\"category\":\"food\",\"limit\":2}");
        assertThat(food.get("matches").asLong()).isEqualTo(4);
        assertThat(food.get("total").asText()).isEqualTo("₹3,500");
        assertThat(food.get("expenses")).hasSize(2);

        JsonNode search = call("find_expenses", "{\"search\":\"lunch\"}");
        assertThat(search.get("matches").asLong()).isEqualTo(1);
        assertThat(search.at("/expenses/0/description").asText()).isEqualTo("office lunch");

        JsonNode high = call("find_expenses", "{\"anomaliesOnly\":true}");
        assertThat(high.get("matches").asLong()).isEqualTo(1);
        assertThat(high.at("/expenses/0/amount").asText()).isEqualTo("₹3,000");
        assertThat(high.at("/expenses/0/high").asText()).isEqualTo("3.9× category average");   // 3000 / avg 780 (all Food)
    }

    @Test
    void savingInsightsComeThrough() throws Exception {
        JsonNode r = call("get_saving_insights", "{\"month\":\"2026-10\"}");
        assertThat(r.get("insights")).isNotEmpty();
        assertThat(r.at("/insights/0/amountText").asText()).startsWith("₹");
    }

    @Test
    void badArgumentsComeBackAsErrors() throws Exception {
        assertThat(call("get_spend_summary", "{\"from\":\"01-10-2026\",\"to\":\"2026-10-31\"}").get("error").asText())
                .contains("YYYY-MM-DD");
        assertThat(call("get_spend_summary", "{\"from\":\"2025-01-01\",\"to\":\"2026-10-31\"}").get("error").asText())
                .contains("366");
        assertThat(call("find_expenses", "{\"category\":\"Pets\"}").get("error").asText()).contains("unknown category");
        assertThat(call("find_expenses", "{\"month\":\"Oct\"}").get("error").asText()).contains("YYYY-MM");
        assertThat(call("get_saving_insights", "{}").get("error").asText()).contains("YYYY-MM");
        assertThat(call("save_note", "{\"note\":\"   \"}").get("error").asText()).contains("1-200");
        assertThat(call("delete_everything", "{}").get("error").asText()).contains("unknown tool");
        assertThat(call("top_vendors", "not json").get("error").asText()).contains("JSON");
    }

    @Test
    void findExpensesShowsAtMostTwenty() throws Exception {
        StringJoiner csv = new StringJoiner("\n", "date,amount,vendor\n", "\n");
        for (int i = 1; i <= 25; i++) {
            csv.add("2026-08-%02d,%d,Chai Point".formatted(i, 10 + i));
        }
        upload(csv.toString()).andReturn();
        JsonNode r = call("find_expenses", "{\"search\":\"chai\",\"limit\":100}");
        assertThat(r.get("matches").asLong()).isEqualTo(25);
        assertThat(r.get("expenses")).hasSize(20);
    }
}
