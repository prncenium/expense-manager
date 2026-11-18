package com.expensemanager.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompareIT extends AbstractIT {

    @Test
    void comparesTwoMonthsPerCategory() throws Exception {
        upload("""
                date,amount,vendor
                2026-09-30,5000,Swiggy
                2026-10-01,100,Swiggy
                2026-10-31,300,Uber
                2026-11-01,118,Swiggy
                2026-11-15,40,Chai Point
                2026-12-01,999,Swiggy
                """);
        JsonNode r = getJson("/api/compare?aFrom=2026-10-01&aTo=2026-10-31&bFrom=2026-11-01&bTo=2026-11-30");
        assertThat(r.at("/a/days").asLong()).isEqualTo(31);
        assertThat(r.at("/b/days").asLong()).isEqualTo(30);
        assertThat(r.at("/a/count").asLong()).isEqualTo(2);
        assertThat(r.at("/b/count").asLong()).isEqualTo(2);
        assertThat(r.at("/a/from").asText()).isEqualTo("2026-10-01");
        assertThat(r.at("/total/a").decimalValue()).isEqualByComparingTo("400");
        assertThat(r.at("/total/b").decimalValue()).isEqualByComparingTo("158");
        assertThat(r.at("/total/changePct").decimalValue()).isEqualByComparingTo("-60.5");

        JsonNode rows = r.get("rows");
        assertThat(rows).hasSize(3);
        assertThat(rows.at("/0/category").asText()).isEqualTo("Food");
        assertThat(rows.at("/0/changePct").decimalValue()).isEqualByComparingTo("18.0");
        assertThat(rows.at("/1/category").asText()).isEqualTo("Other");
        assertThat(rows.at("/1/changePct").isNull()).isTrue();      // nothing in A: "new"
        assertThat(rows.at("/2/category").asText()).isEqualTo("Transport");
        assertThat(rows.at("/2/changePct").decimalValue()).isEqualByComparingTo("-100.0");
    }

    @Test
    void customRangesMayOverlap() throws Exception {
        upload("""
                date,amount,vendor
                2026-11-02,100,Swiggy
                2026-11-05,50,Swiggy
                2026-11-09,30,Swiggy
                """);
        JsonNode r = getJson("/api/compare?aFrom=2026-11-01&aTo=2026-11-05&bFrom=2026-11-05&bTo=2026-11-09");
        assertThat(r.at("/total/a").decimalValue()).isEqualByComparingTo("150");
        assertThat(r.at("/total/b").decimalValue()).isEqualByComparingTo("80");
        assertThat(r.at("/a/days").asLong()).isEqualTo(5);
    }

    @Test
    void emptyPeriodsGiveZeroTotals() throws Exception {
        JsonNode r = getJson("/api/compare?aFrom=2026-11-02&aTo=2026-11-08&bFrom=2026-11-09&bTo=2026-11-15");
        assertThat(r.get("rows")).isEmpty();
        assertThat(r.at("/total/a").decimalValue()).isEqualByComparingTo("0");
        assertThat(r.at("/total/changePct").isNull()).isTrue();
        assertThat(r.at("/b/count").asLong()).isZero();
    }

    @Test
    void badRangesAre400() throws Exception {
        mvc.perform(get("/api/compare?aFrom=2026-11-10&aTo=2026-11-01&bFrom=2026-11-01&bTo=2026-11-02"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/compare?aFrom=2026-11-01&aTo=nope&bFrom=2026-11-01&bTo=2026-11-02"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/compare?aFrom=2024-01-01&aTo=2026-01-01&bFrom=2026-11-01&bTo=2026-11-02"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/compare?aFrom=2026-11-01&aTo=2026-11-02"))
                .andExpect(status().isBadRequest());
    }
}
