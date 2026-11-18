package com.expensemanager.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnomalyIT extends AbstractIT {

    /** SPEC §6 example: Food 200, 300, 250, 3000 → avg 937.50 → only 3000 is flagged (3.2× avg). */
    private void addFoodExample() throws Exception {
        addExpense("2026-10-05", "200", "Zomato").andExpect(status().isCreated());
        addExpense("2026-10-06", "300", "Swiggy").andExpect(status().isCreated());
        addExpense("2026-11-01", "250", "Zomato").andExpect(status().isCreated());
        addExpense("2026-11-02", "3000", "Zomato")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("Food"))
                .andExpect(jsonPath("$.anomaly").value(true))
                .andExpect(jsonPath("$.timesAvg").value(3.2))
                .andExpect(jsonPath("$.categoryAvg").value(937.5));
    }

    @Test
    void specExampleFlagsOnlyTheBigOne() throws Exception {
        addFoodExample();
        JsonNode list = items("/api/expenses");
        assertThat(list).hasSize(4);
        for (JsonNode e : list) {
            assertThat(e.get("anomaly").asBoolean())
                    .as("anomaly flag for %s", e.get("amount"))
                    .isEqualTo(e.get("amount").decimalValue().intValue() == 3000);
            assertThat(e.get("categoryAvg").decimalValue()).isEqualByComparingTo("937.50");
        }
        assertThat(items("/api/expenses?anomaliesOnly=true")).hasSize(1);
    }

    @Test
    void threeRowsCanNeverBeFlagged() throws Exception {
        addExpense("2026-11-01", "1", "Uber").andExpect(status().isCreated());
        addExpense("2026-11-02", "1", "Ola").andExpect(status().isCreated());
        addExpense("2026-11-03", "10000000", "Uber").andExpect(status().isCreated());
        JsonNode list = items("/api/expenses");
        assertThat(list).hasSize(3);
        assertThat(list).allSatisfy(e -> assertThat(e.get("anomaly").asBoolean()).isFalse());
    }

    @Test
    void filteringByMonthDoesNotChangeAverageOrFlags() throws Exception {
        addFoodExample();

        JsonNode nov = items("/api/expenses?month=2026-11");
        assertThat(nov).hasSize(2);
        assertThat(nov).allSatisfy(e -> assertThat(e.get("categoryAvg").decimalValue()).isEqualByComparingTo("937.50"));
        assertThat(flagged(nov)).containsExactly("3000.00");

        JsonNode oct = items("/api/expenses?month=2026-10");
        assertThat(oct).hasSize(2);
        assertThat(oct).allSatisfy(e -> assertThat(e.get("categoryAvg").decimalValue()).isEqualByComparingTo("937.50"));
        assertThat(flagged(oct)).isEmpty();

        assertThat(items("/api/expenses?month=2026-11&anomaliesOnly=true")).hasSize(1);
        assertThat(items("/api/expenses?month=2026-10&anomaliesOnly=true")).isEmpty();
        assertThat(items("/api/expenses?categoryId=1&anomaliesOnly=true")).hasSize(1);

        // the dashboard uses the same query
        assertThat(getJson("/api/dashboard?month=2026-11").at("/anomalies/count").asInt()).isEqualTo(1);
        assertThat(getJson("/api/dashboard?month=2026-10").at("/anomalies/count").asInt()).isZero();
        assertThat(getJson("/api/dashboard").at("/anomalies/count").asInt()).isEqualTo(1);
    }

    @Test
    void badMonthIs400() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/expenses?month=11-2026"))
                .andExpect(status().isBadRequest());
    }

    private static List<String> flagged(JsonNode list) {
        List<String> out = new ArrayList<>();
        list.forEach(e -> {
            if (e.get("anomaly").asBoolean()) {
                out.add(e.get("amount").decimalValue().setScale(2).toPlainString());
            }
        });
        return out;
    }
}
