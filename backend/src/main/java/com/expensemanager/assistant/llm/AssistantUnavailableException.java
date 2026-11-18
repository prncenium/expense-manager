package com.expensemanager.assistant.llm;

/** 503: no API key configured, or every provider failed (ASSISTANT-SPEC §3.4–3.5). */
public class AssistantUnavailableException extends RuntimeException {
    public AssistantUnavailableException(String message) {
        super(message);
    }
}
