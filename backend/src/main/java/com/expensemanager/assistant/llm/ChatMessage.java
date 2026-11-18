package com.expensemanager.assistant.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** One message in the OpenAI-compatible chat format that both Gemini and Groq accept. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessage(
        String role,
        String content,
        @JsonProperty("tool_calls") List<ToolCall> toolCalls,
        @JsonProperty("tool_call_id") String toolCallId) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null);
    }

    /** The result of one tool call, sent back to the model. */
    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage("tool", content, null, toolCallId);
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /**
     * {@code extraContent} is provider data that must be sent back unchanged with the call:
     * newer Gemini models put a "thought signature" there and reject the next request without it.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolCall(String id, String type, Function function,
                           @JsonProperty("extra_content") JsonNode extraContent) {

        public ToolCall(String id, String type, Function function) {
            this(id, type, function, null);
        }

        public ToolCall withoutExtraContent() {
            return extraContent == null ? this : new ToolCall(id, type, function, null);
        }

        /** {@code arguments} is a JSON object encoded as a string, as the API sends it. */
        public record Function(String name, String arguments) {
        }
    }
}
