package com.expensemanager.assistant.llm;

/** One OpenAI-compatible endpoint. The key is never logged or sent to the browser. */
public record LlmProvider(String name, String baseUrl, String apiKey, String model) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "LlmProvider[" + name + ", " + model + "]";
    }
}
