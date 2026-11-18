package com.expensemanager.assistant.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * Tries each configured provider in order (Gemini, then Groq). The first answer wins; when
 * all fail the user gets a friendly 503 (ASSISTANT-SPEC §3.4). Stateless: holds only the clients.
 */
public class FallbackLlm implements LlmClient {

    static final String OFF = "Muneem is off: add GEMINI_API_KEY or GROQ_API_KEY on the server (see README).";
    static final String BUSY = "Muneem is busy right now (free limit reached). Try again in a minute.";

    private static final Logger log = LoggerFactory.getLogger(FallbackLlm.class);

    private final List<OpenAiCompatibleClient> clients;

    public FallbackLlm(List<OpenAiCompatibleClient> clients) {
        this.clients = List.copyOf(clients);
    }

    @Override
    public LlmReply chat(List<ChatMessage> messages, List<ToolDef> tools) {
        if (clients.isEmpty()) {
            throw new AssistantUnavailableException(OFF);
        }
        for (OpenAiCompatibleClient client : clients) {
            try {
                return new LlmReply(client.complete(messages, tools), client.name());
            } catch (RuntimeException e) {
                log.warn("{} failed, trying the next provider: {}", client.name(), reason(e));
            }
        }
        throw new AssistantUnavailableException(BUSY);
    }

    @Override
    public List<String> providers() {
        return clients.stream().map(OpenAiCompatibleClient::name).toList();
    }

    /** Short reason for the log: status code or exception type, never headers (the key). */
    private static String reason(RuntimeException e) {
        if (e instanceof RestClientResponseException http) {
            return "HTTP " + http.getStatusCode().value();
        }
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
    }
}
