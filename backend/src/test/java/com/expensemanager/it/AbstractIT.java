package com.expensemanager.it;

import com.expensemanager.service.CategorizationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Runs against the local test DB expense_manager_test (Flyway migrates it); expenses are wiped before each test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractIT {

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    private CategorizationService categorization;

    @BeforeEach
    void cleanExpenses() {
        jdbc.update("DELETE FROM expenses");
        jdbc.update("DELETE FROM assistant_notes");
        // rules added by a test are removed too: the seed rows all share one created_at (one transaction)
        jdbc.update("DELETE FROM vendor_rules WHERE created_at > (SELECT MIN(created_at) FROM vendor_rules)");
        categorization.invalidate();
    }

    protected ResultActions addExpense(String date, String amount, String vendor) throws Exception {
        String body = """
                {"date":"%s","amount":%s,"vendor":"%s"}""".formatted(date, amount, vendor);
        return mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions upload(String csv) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "expenses.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
        return mvc.perform(multipart("/api/expenses/upload").file(file));
    }

    protected JsonNode getJson(String url) throws Exception {
        return json.readTree(mvc.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** The items of one page of GET /api/expenses (default page size 50). */
    protected JsonNode items(String url) throws Exception {
        return getJson(url).get("items");
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
