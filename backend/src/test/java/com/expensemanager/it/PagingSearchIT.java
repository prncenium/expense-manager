package com.expensemanager.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PagingSearchIT extends AbstractIT {

    private void load(int n) throws Exception {
        StringBuilder csv = new StringBuilder("date,amount,vendor,description\n");
        for (int i = 1; i <= n; i++) {
            csv.append("2026-10-%02d,%d,Shop %d,item %d\n".formatted((i % 28) + 1, 100 + i, i, i));
        }
        upload(csv.toString());
    }

    @Test
    void pagesCoverEveryRowOnceWithTotals() throws Exception {
        load(25);
        JsonNode p0 = getJson("/api/expenses?size=10");
        assertThat(p0.get("items")).hasSize(10);
        assertThat(p0.get("totalItems").asInt()).isEqualTo(25);
        assertThat(p0.get("totalPages").asInt()).isEqualTo(3);
        // 25 rows of 100+i: 2500 + 325
        assertThat(p0.get("totalAmount").decimalValue()).isEqualByComparingTo("2825");

        Set<Long> ids = new HashSet<>();
        for (int page = 0; page < 3; page++) {
            getJson("/api/expenses?size=10&page=" + page).get("items").forEach(e -> ids.add(e.get("id").asLong()));
        }
        assertThat(ids).hasSize(25);

        JsonNode past = getJson("/api/expenses?size=10&page=9");
        assertThat(past.get("items")).isEmpty();
        assertThat(past.get("totalItems").asInt()).isEqualTo(25);
    }

    @Test
    void searchesVendorAndDescription() throws Exception {
        upload("""
                date,amount,vendor,description
                2026-11-01,100,Swiggy,dinner
                2026-11-02,200,Uber,ride to SWIGGY office
                2026-11-03,300,Amazon,books
                2026-11-04,400,50% Off Store,sale
                """);
        assertThat(getJson("/api/expenses?q=swiggy").get("totalItems").asInt()).isEqualTo(2);
        assertThat(getJson("/api/expenses?q=BOOK").get("totalItems").asInt()).isEqualTo(1);
        assertThat(search("%")).isEqualTo(1);    // % is literal
        assertThat(search("_")).isZero();        // _ is literal
        assertThat(search("  ")).isEqualTo(4);   // blank: all
    }

    private int search(String q) throws Exception {
        return json.readTree(mvc.perform(get("/api/expenses").param("q", q)).andReturn().getResponse()
                .getContentAsString()).get("totalItems").asInt();
    }

    @Test
    void anomalyFlagsAreTheSameOnEveryPage() throws Exception {
        upload("""
                date,amount,vendor
                2026-11-01,200,Swiggy
                2026-11-02,300,Swiggy
                2026-11-03,250,Swiggy
                2026-11-04,3000,Swiggy
                """);
        JsonNode first = getJson("/api/expenses?size=1&page=0").at("/items/0");
        assertThat(first.get("amount").decimalValue()).isEqualByComparingTo("3000");
        assertThat(first.get("anomaly").asBoolean()).isTrue();
        assertThat(getJson("/api/expenses?size=1&page=1").at("/items/0/anomaly").asBoolean()).isFalse();
    }

    @Test
    void badPagingIs400() throws Exception {
        mvc.perform(get("/api/expenses?size=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/expenses?size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/expenses?page=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/expenses?q=" + "x".repeat(101))).andExpect(status().isBadRequest());
    }
}
