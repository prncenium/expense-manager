package com.expensemanager.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantStatusIT extends AbstractIT {

    @Test
    void offWithoutKeys() throws Exception {
        JsonNode status = getJson("/api/assistant/status");
        assertThat(status.get("enabled").asBoolean()).isFalse();
        assertThat(status.get("providers")).isEmpty();
    }
}
