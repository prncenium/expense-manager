package com.expensemanager.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TopVendorsIT extends AbstractIT {

    @Test
    void spellingsAreGroupedUnderTheMostUsedOne() throws Exception {
        upload("""
                date,amount,vendor
                2026-11-01,100,swiggy
                2026-11-02,100,swiggy
                2026-11-03,100,Swiggy
                2026-11-04,100,Swiggy
                2026-11-05,100,Swiggy
                2026-11-06,100,Swiggy
                2026-11-07,101, Swiggy
                2026-11-01,50,Uber
                """).andExpect(status().isOk());

        JsonNode top = getJson("/api/dashboard").get("topVendors");
        assertThat(top).hasSize(2);
        assertThat(top.get(0).get("vendor").asText()).isEqualTo("Swiggy");
        assertThat(top.get(0).get("count").asInt()).isEqualTo(7);
        assertThat(top.get(0).get("total").decimalValue()).isEqualByComparingTo("701");
        assertThat(top.get(1).get("vendor").asText()).isEqualTo("Uber");
    }

    @Test
    void respectsMonthFilter() throws Exception {
        upload("""
                date,amount,vendor
                2026-10-01,900,Amazon
                2026-11-01,100,Swiggy
                """).andExpect(status().isOk());

        assertThat(getJson("/api/dashboard").at("/topVendors/0/vendor").asText()).isEqualTo("Amazon");
        JsonNode nov = getJson("/api/dashboard?month=2026-11");
        assertThat(nov.get("topVendors")).hasSize(1);
        assertThat(nov.at("/topVendors/0/vendor").asText()).isEqualTo("Swiggy");
        assertThat(nov.at("/totals/count").asInt()).isEqualTo(1);
        assertThat(nov.at("/totals/spend").decimalValue()).isEqualByComparingTo("100");
    }
}
