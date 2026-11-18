package com.expensemanager.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UncategorizedIT extends AbstractIT {

    private static final int FOOD = 1, HEALTH = 8, OTHER = 9;

    private ResultActions addRule(String keyword, int categoryId, String label) throws Exception {
        String body = label == null
                ? "{\"keyword\":\"%s\",\"categoryId\":%d}".formatted(keyword, categoryId)
                : "{\"keyword\":\"%s\",\"categoryId\":%d,\"label\":\"%s\"}".formatted(keyword, categoryId, label);
        return mvc.perform(post("/api/rules").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void otherVendorsAreListedAndGrouped() throws Exception {
        upload("""
                date,amount,vendor
                2026-11-01,100,Chai Point
                2026-11-05,150,chai point
                2026-11-02,900,Cult.fit
                2026-11-03,200,Swiggy
                """);
        JsonNode panel = getJson("/api/vendors/uncategorized");
        assertThat(panel).hasSize(2);
        assertThat(panel.at("/0/vendor").asText()).isEqualTo("Cult.fit");
        assertThat(panel.at("/0/keyword").asText()).isEqualTo("cult.fit");
        assertThat(panel.at("/1/count").asInt()).isEqualTo(2);
        assertThat(panel.at("/1/total").decimalValue()).isEqualByComparingTo("250");
        assertThat(panel.at("/1/lastDate").asText()).isEqualTo("2026-11-05");
        assertThat(getJson("/api/dashboard").get("uncategorizedVendors").asInt()).isEqualTo(2);
    }

    @Test
    void creatingARuleMovesOtherExpensesAndEmptiesThePanel() throws Exception {
        upload("""
                date,amount,vendor
                2026-11-01,100,Chai Point
                2026-11-05,150,Chai Point MG Road
                2026-11-03,200,Swiggy
                """);
        addRule("chai point", FOOD, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recategorized").value(2));

        assertThat(getJson("/api/vendors/uncategorized")).isEmpty();
        assertThat(items("/api/expenses?categoryId=" + FOOD)).hasSize(3);
    }

    @Test
    void ruleToOtherWithNicknameLabelsTheExpenses() throws Exception {
        upload("""
                date,amount,vendor
                2026-11-02,900,Cult.fit
                2026-11-04,50,Chai Point
                """);
        addRule("cult", OTHER, "Gym")
                .andExpect(jsonPath("$.label").value("Gym"))
                .andExpect(jsonPath("$.recategorized").value(1));

        JsonNode panel = getJson("/api/vendors/uncategorized");
        assertThat(panel).hasSize(1);
        assertThat(panel.at("/0/vendor").asText()).isEqualTo("Chai Point");

        JsonNode gym = items("/api/expenses?categoryId=" + OTHER + "&label=gym");
        assertThat(gym).hasSize(1);
        assertThat(gym.at("/0/label").asText()).isEqualTo("Gym");
        assertThat(getJson("/api/labels").toString()).contains("Gym");

        // new expenses get the label straight away
        assertThat(body(addExpense("2026-11-10", "900", "CULT Indiranagar")).get("label").asText()).isEqualTo("Gym");
    }

    @Test
    void expensesInARealCategoryAreNeverMoved() throws Exception {
        upload("""
                date,amount,vendor
                2026-11-01,100,Swiggy Pharmacy
                2026-11-02,100,Chai Point
                """);
        // "Swiggy Pharmacy" is already Food; a longer Health rule only applies to new expenses
        addRule("swiggy pharma", HEALTH, null).andExpect(jsonPath("$.recategorized").value(0));
        assertThat(items("/api/expenses?categoryId=" + FOOD)).hasSize(1);
    }

    @Test
    void editingARuleAlsoReapplies() throws Exception {
        upload("""
                date,amount,vendor
                2026-11-01,100,Chai Point
                """);
        JsonNode rule = body(addRule("chaipoint", FOOD, null));
        assertThat(rule.get("recategorized").asInt()).isZero();
        mvc.perform(put("/api/rules/" + rule.get("id").asLong()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"chai point\",\"categoryId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recategorized").value(1));
    }

    @Test
    void labelWithARealCategoryIs400() throws Exception {
        addRule("dominos", FOOD, "Pizza")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.label").value("is only for Other"));
    }
}
