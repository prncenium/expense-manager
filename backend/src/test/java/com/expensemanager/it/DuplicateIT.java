package com.expensemanager.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DuplicateIT extends AbstractIT {

    private static final String CSV = """
            date,amount,vendor,description
            2026-11-01,250,Swiggy,Lunch
            2026-11-02,"₹1,499",Amazon,Headphones
            2026-11-03,120,Chai Point,Tea
            """;

    @Test
    void uploadingTheSameFileTwiceSavesNothingTheSecondTime() throws Exception {
        upload(CSV).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.saved").value(3))
                .andExpect(jsonPath("$.duplicates").value(0));

        JsonNode second = body(upload(CSV).andExpect(status().isOk()));
        assertThat(second.get("saved").asInt()).isZero();
        assertThat(second.get("skipped").asInt()).isEqualTo(3);
        assertThat(second.get("duplicates").asInt()).isEqualTo(3);
        assertThat(second.get("errors")).allSatisfy(e -> assertThat(e.get("reason").asText()).isEqualTo("duplicate"));

        assertThat(items("/api/expenses")).hasSize(3);
    }

    @Test
    void duplicateInsideOneFileIsCaughtIgnoringCaseAndSpaces() throws Exception {
        JsonNode result = body(upload("""
                date,amount,vendor
                2026-11-01,250,Swiggy
                01-11-2026,250.00,  swiggy\s
                2026-11-01,251,Swiggy
                """).andExpect(status().isOk()));
        assertThat(result.get("saved").asInt()).isEqualTo(2);
        assertThat(result.get("duplicates").asInt()).isEqualTo(1);
        assertThat(result.get("errors")).singleElement().satisfies(e -> {
            assertThat(e.get("row").asInt()).isEqualTo(3);
            assertThat(e.get("reason").asText()).isEqualTo("duplicate");
        });
    }

    @Test
    void manualDuplicateIsSavedButFlagged() throws Exception {
        addExpense("2026-11-01", "250", "Swiggy").andExpect(jsonPath("$.possibleDuplicate").value(false));
        addExpense("2026-11-01", "250.00", " SWIGGY ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.possibleDuplicate").value(true));
        assertThat(items("/api/expenses")).hasSize(2);
    }

    @Test
    void badRowsAreSkippedGoodRowsSaved() throws Exception {
        upload("""
                date,amount,vendor
                2026-11-01,250,Swiggy
                2026-11-01,-5,Zomato
                2099-01-01,100,Uber
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(1))
                .andExpect(jsonPath("$.skipped").value(2))
                .andExpect(jsonPath("$.errors[0].row").value(3))
                .andExpect(jsonPath("$.errors[0].reason").value("amount must be > 0"))
                .andExpect(jsonPath("$.errors[1].reason").value("date cannot be in the future"));
    }

    @Test
    void wrongHeaderSavesNothing() throws Exception {
        upload("when,how much,who\n2026-11-01,250,Swiggy\n")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
        assertThat(items("/api/expenses")).isEmpty();
    }
}
