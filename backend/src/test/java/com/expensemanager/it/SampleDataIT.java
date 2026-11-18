package com.expensemanager.it;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SampleDataIT extends AbstractIT {

    @Test
    void loadsOnEmptyDatabase() throws Exception {
        mvc.perform(post("/api/sample-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(45))
                .andExpect(jsonPath("$.saved").value(42))
                .andExpect(jsonPath("$.skipped").value(3))
                .andExpect(jsonPath("$.duplicates").value(0))
                .andExpect(jsonPath("$.anomaliesFound").value(3));
        assertThat(items("/api/expenses")).hasSize(42);
    }

    @Test
    void conflictWhenExpensesAlreadyExist() throws Exception {
        addExpense("2026-11-01", "100", "Swiggy").andExpect(status().isCreated());
        mvc.perform(post("/api/sample-data")).andExpect(status().isConflict());
        assertThat(items("/api/expenses")).hasSize(1);
    }
}
