package com.expensemanager.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MonthOverMonthIT extends AbstractIT {

    @Test
    void comparesTheChosenMonthWithTheOneBefore() throws Exception {
        upload("""
                date,amount,vendor
                2026-09-10,5000,Swiggy
                2026-10-01,100,Swiggy
                2026-10-02,300,Uber
                2026-11-01,118,Swiggy
                2026-11-03,40,Chai Point
                """);
        JsonNode mom = getJson("/api/dashboard?month=2026-11").get("monthOverMonth");
        assertThat(mom.get("month").asText()).isEqualTo("2026-11");
        assertThat(mom.get("previousMonth").asText()).isEqualTo("2026-10");
        assertThat(mom.at("/total/current").decimalValue()).isEqualByComparingTo("158");
        assertThat(mom.at("/total/previous").decimalValue()).isEqualByComparingTo("400");
        assertThat(mom.at("/total/changePct").decimalValue()).isEqualByComparingTo("-60.5");

        JsonNode rows = mom.get("rows");
        assertThat(rows).hasSize(3);
        assertThat(rows.at("/0/category").asText()).isEqualTo("Food");
        assertThat(rows.at("/0/changePct").decimalValue()).isEqualByComparingTo("18.0");
        assertThat(rows.at("/1/category").asText()).isEqualTo("Other");
        assertThat(rows.at("/1/changePct").isNull()).isTrue();      // nothing last month: "new"
        assertThat(rows.at("/2/category").asText()).isEqualTo("Transport");
        assertThat(rows.at("/2/changePct").decimalValue()).isEqualByComparingTo("-100.0");

        // October vs September: the September 5000 is now "previous"
        JsonNode oct = getJson("/api/dashboard?month=2026-10").get("monthOverMonth");
        assertThat(oct.at("/total/previous").decimalValue()).isEqualByComparingTo("5000");
    }

    @Test
    void otherIsSplitByLabel() throws Exception {
        jdbc.update("INSERT INTO vendor_rules (keyword, category_id, label) VALUES ('cult', 9, 'Gym')");
        upload("""
                date,amount,vendor
                2026-11-01,900,Cult.fit
                2026-11-02,40,Chai Point
                """);
        JsonNode rows = getJson("/api/dashboard?month=2026-11").at("/monthOverMonth/rows");
        assertThat(rows).hasSize(2);
        assertThat(rows.at("/0/label").asText()).isEqualTo("Gym");
        assertThat(rows.at("/1/label").isNull()).isTrue();
    }

    @Test
    void emptyMonthsGiveZeroTotals() throws Exception {
        JsonNode mom = getJson("/api/dashboard?month=2026-11").get("monthOverMonth");
        assertThat(mom.get("rows")).isEmpty();
        assertThat(mom.at("/total/current").decimalValue()).isEqualByComparingTo("0");
        assertThat(mom.at("/total/changePct").isNull()).isTrue();
    }
}
