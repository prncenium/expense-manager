package com.expensemanager.assistant.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Calls {@code POST {baseUrl}/chat/completions} on one provider. Any failure (HTTP error,
 * timeout, empty answer) is thrown so {@link FallbackLlm} can try the next provider.
 */
public class OpenAiCompatibleClient {

    static final double TEMPERATURE = 0.3;

    private final String name;
    private final String model;
    private final RestClient http;

    /** {@code http} must already carry the base URL and the {@code Authorization} header. */
    public OpenAiCompatibleClient(String name, String model, RestClient http) {
        this.name = name;
        this.model = model;
        this.http = http;
    }

    public String name() {
        return name;
    }

    public ChatMessage complete(List<ChatMessage> messages, List<ToolDef> tools) {
        ChatRequest request = new ChatRequest(model, forThisProvider(messages),
                tools == null || tools.isEmpty() ? null : tools, TEMPERATURE);
        ChatResponse response = http.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatResponse.class);
        ChatMessage message = response == null || response.choices() == null || response.choices().isEmpty()
                ? null : response.choices().get(0).message();
        if (message == null || (!message.hasToolCalls() && (message.content() == null || message.content().isBlank()))) {
            throw new IllegalStateException(name + " returned an empty answer");
        }
        return message;
    }

    /**
     * Gemini needs its own extra_content back on tool calls; other providers get it removed (they
     * may reject unknown fields), which matters when Groq takes over a turn Gemini started.
     */
    private List<ChatMessage> forThisProvider(List<ChatMessage> messages) {
        if (KEEPS_EXTRA_CONTENT.equals(name)) {
            return messages;
        }
        return messages.stream().map(m -> !m.hasToolCalls() ? m : new ChatMessage(m.role(), m.content(),
                m.toolCalls().stream().map(ChatMessage.ToolCall::withoutExtraContent).toList(), m.toolCallId()))
                .toList();
    }

    static final String KEEPS_EXTRA_CONTENT = "GEMINI";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ChatRequest(String model, List<ChatMessage> messages, List<ToolDef> tools, Double temperature) {
    }

    record ChatResponse(List<Choice> choices) {
        record Choice(ChatMessage message) {
        }
    }
}
