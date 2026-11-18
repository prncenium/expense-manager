package com.expensemanager.assistant.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** ASSISTANT-SPEC §3.4: Gemini first, Groq on any failure, friendly 503 when both fail. No real network. */
class FallbackLlmTest {

    private static final String URL = "/chat/completions";
    private static final String TEXT_REPLY = """
            {"choices":[{"message":{"role":"assistant","content":"Food was the most: ₹6,200."}}]}""";

    private MockRestServiceServer gemini;
    private MockRestServiceServer groq;
    private FallbackLlm llm;

    @BeforeEach
    void setUp() {
        RestClient.Builder g = RestClient.builder().defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer g-key");
        RestClient.Builder q = RestClient.builder().defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer q-key");
        gemini = MockRestServiceServer.bindTo(g).build();
        groq = MockRestServiceServer.bindTo(q).build();
        llm = new FallbackLlm(List.of(
                new OpenAiCompatibleClient("GEMINI", "gemini-model", g.build()),
                new OpenAiCompatibleClient("GROQ", "groq-model", q.build())));
    }

    private static List<ChatMessage> question() {
        return List.of(ChatMessage.system("You are Muneem."), ChatMessage.user("Where did I spend most?"));
    }

    @Test
    void geminiAnswersFirstAndGroqIsNotCalled() {
        gemini.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer g-key"))
                .andExpect(jsonPath("$.model").value("gemini-model"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("Where did I spend most?"))
                .andExpect(jsonPath("$.messages[1].tool_calls").doesNotExist())
                .andExpect(jsonPath("$.tools").doesNotExist())
                .andRespond(withSuccess(TEXT_REPLY, MediaType.APPLICATION_JSON));
        groq.expect(never(), requestTo(URL));

        LlmReply reply = llm.chat(question(), List.of());

        assertThat(reply.provider()).isEqualTo("GEMINI");
        assertThat(reply.message().content()).isEqualTo("Food was the most: ₹6,200.");
        gemini.verify();
        groq.verify();
    }

    @Test
    void rateLimitOnGeminiFallsBackToGroq() {
        gemini.expect(requestTo(URL)).andRespond(withTooManyRequests());
        groq.expect(requestTo(URL)).andExpect(jsonPath("$.model").value("groq-model"))
                .andRespond(withSuccess(TEXT_REPLY, MediaType.APPLICATION_JSON));

        assertThat(llm.chat(question(), List.of()).provider()).isEqualTo("GROQ");
        groq.verify();
    }

    @Test
    void timeoutOnGeminiFallsBackToGroq() {
        gemini.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));
        groq.expect(requestTo(URL)).andRespond(withSuccess(TEXT_REPLY, MediaType.APPLICATION_JSON));

        assertThat(llm.chat(question(), List.of()).provider()).isEqualTo("GROQ");
    }

    @Test
    void emptyAnswerFromGeminiFallsBackToGroq() {
        gemini.expect(requestTo(URL)).andRespond(withSuccess("""
                {"choices":[{"message":{"role":"assistant","content":"  "}}]}""", MediaType.APPLICATION_JSON));
        groq.expect(requestTo(URL)).andRespond(withSuccess(TEXT_REPLY, MediaType.APPLICATION_JSON));

        assertThat(llm.chat(question(), List.of()).provider()).isEqualTo("GROQ");
    }

    @Test
    void bothFailingGivesTheBusyMessage() {
        gemini.expect(requestTo(URL)).andRespond(withServerError());
        groq.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> llm.chat(question(), List.of()))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessage(FallbackLlm.BUSY);
    }

    @Test
    void noProvidersMeansOff() {
        FallbackLlm off = new FallbackLlm(List.of());
        assertThat(off.providers()).isEmpty();
        assertThatThrownBy(() -> off.chat(question(), List.of()))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessage(FallbackLlm.OFF);
    }

    @Test
    void sendsToolsAndReadsToolCalls() {
        ToolDef tool = ToolDef.of("get_spend_summary", "Spend per category between two dates",
                Map.of("type", "object", "properties", Map.of("from", Map.of("type", "string"))));
        gemini.expect(requestTo(URL))
                .andExpect(jsonPath("$.tools[0].type").value("function"))
                .andExpect(jsonPath("$.tools[0].function.name").value("get_spend_summary"))
                .andExpect(jsonPath("$.tools[0].function.parameters.type").value("object"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":null,
                          "tool_calls":[{"id":"call_1","type":"function",
                            "function":{"name":"get_spend_summary","arguments":"{\\"from\\":\\"2026-11-01\\"}"}}]}}]}""",
                        MediaType.APPLICATION_JSON));

        ChatMessage message = llm.chat(question(), List.of(tool)).message();

        assertThat(message.hasToolCalls()).isTrue();
        assertThat(message.toolCalls().get(0).id()).isEqualTo("call_1");
        assertThat(message.toolCalls().get(0).function().name()).isEqualTo("get_spend_summary");
        assertThat(message.toolCalls().get(0).function().arguments()).isEqualTo("{\"from\":\"2026-11-01\"}");
    }

    @Test
    void sendsToolResultsInTheApiShape() {
        ChatMessage call = new ChatMessage("assistant", null, List.of(new ChatMessage.ToolCall("call_1", "function",
                new ChatMessage.ToolCall.Function("get_spend_summary", "{}"))), null);
        gemini.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages[2].tool_calls[0].id").value("call_1"))
                .andExpect(jsonPath("$.messages[2].content").doesNotExist())
                .andExpect(jsonPath("$.messages[3].role").value("tool"))
                .andExpect(jsonPath("$.messages[3].tool_call_id").value("call_1"))
                .andRespond(withSuccess(TEXT_REPLY, MediaType.APPLICATION_JSON));

        llm.chat(List.of(question().get(0), question().get(1), call, ChatMessage.tool("call_1", "{\"total\":\"₹0\"}")),
                List.of());
        gemini.verify();
    }

    @Test
    void geminiThoughtSignatureIsKeptForGeminiAndDroppedForGroq() throws Exception {
        com.fasterxml.jackson.databind.JsonNode extra = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("{\"google\":{\"thought_signature\":\"sig-123\"}}");
        ChatMessage call = new ChatMessage("assistant", null, List.of(new ChatMessage.ToolCall("call_1", "function",
                new ChatMessage.ToolCall.Function("get_spend_summary", "{}"), extra)), null);
        List<ChatMessage> turn = List.of(question().get(1), call, ChatMessage.tool("call_1", "{}"));

        gemini.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages[1].tool_calls[0].extra_content.google.thought_signature").value("sig-123"))
                .andRespond(withServerError());
        groq.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages[1].tool_calls[0].id").value("call_1"))
                .andExpect(jsonPath("$.messages[1].tool_calls[0].extra_content").doesNotExist())
                .andRespond(withSuccess(TEXT_REPLY, MediaType.APPLICATION_JSON));

        assertThat(llm.chat(turn, List.of()).provider()).isEqualTo("GROQ");
        gemini.verify();
        groq.verify();
    }

    @Test
    void readsExtraContentFromAToolCall() {
        gemini.expect(requestTo(URL)).andRespond(withSuccess("""
                {"choices":[{"message":{"role":"assistant","tool_calls":[{"id":"c1","type":"function",
                  "function":{"name":"top_vendors","arguments":"{}"},
                  "extra_content":{"google":{"thought_signature":"abc"}}}]}}]}""", MediaType.APPLICATION_JSON));
        ChatMessage m = llm.chat(question(), List.of()).message();
        assertThat(m.toolCalls().get(0).extraContent().at("/google/thought_signature").asText()).isEqualTo("abc");
    }
}
