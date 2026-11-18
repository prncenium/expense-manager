package com.expensemanager.it;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExportIT extends AbstractIT {

    private MockHttpServletResponse export(String query) throws Exception {
        return mvc.perform(get("/api/expenses/export" + query)).andExpect(status().isOk()).andReturn().getResponse();
    }

    @Test
    void exportsFilteredRowsWithHeader() throws Exception {
        upload("""
                date,amount,vendor,description
                2026-10-01,100,Swiggy,lunch
                2026-11-01,250.5,Swiggy,"dinner, late"
                2026-11-02,90,=HYPERLINK(1),odd
                """);
        MockHttpServletResponse res = export("?month=2026-11");
        assertThat(res.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"expenses-2026-11.csv\"");
        assertThat(res.getContentType()).startsWith("text/csv");
        String[] lines = res.getContentAsString(StandardCharsets.UTF_8).split("\r\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo("date,amount,vendor,description,category,label,high_expense");
        assertThat(lines[1]).isEqualTo("2026-11-02,90.00,'=HYPERLINK(1),odd,Other,,no");
        assertThat(lines[2]).isEqualTo("2026-11-01,250.50,Swiggy,\"dinner, late\",Food,,no");

        assertThat(export("?q=lunch").getContentAsString(StandardCharsets.UTF_8).split("\r\n")).hasSize(2);
        assertThat(export("").getHeader("Content-Disposition")).contains("expenses-all.csv");
    }

    @Test
    void exportedFileImportsBackAsDuplicates() throws Exception {
        upload("""
                date,amount,vendor
                2026-11-01,100,Swiggy
                2026-11-02,200,Uber
                """);
        String csv = export("").getContentAsString(StandardCharsets.UTF_8);
        upload(csv).andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(0))
                .andExpect(jsonPath("$.duplicates").value(2));
    }

    @Test
    void badFilterIs400() throws Exception {
        mvc.perform(get("/api/expenses/export?month=nope")).andExpect(status().isBadRequest());
    }
}
