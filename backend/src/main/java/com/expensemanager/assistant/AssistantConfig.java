package com.expensemanager.assistant;

import com.expensemanager.assistant.llm.FallbackLlm;
import com.expensemanager.assistant.llm.LlmClient;
import com.expensemanager.assistant.llm.LlmProvider;
import com.expensemanager.assistant.llm.OpenAiCompatibleClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/**
 * Wires the providers from {@code app.assistant.*}. Only providers with a key are used, Gemini first.
 * Uses the plain JDK HTTP connection (no client pool, no extra threads) to keep memory low (§3.12).
 */
@Configuration
public class AssistantConfig {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    LlmClient llmClient(RestClient.Builder builder,
                        @Value("${app.assistant.timeout:20s}") Duration timeout,
                        @Value("${app.assistant.gemini.base-url}") String geminiUrl,
                        @Value("${app.assistant.gemini.api-key:}") String geminiKey,
                        @Value("${app.assistant.gemini.model}") String geminiModel,
                        @Value("${app.assistant.groq.base-url}") String groqUrl,
                        @Value("${app.assistant.groq.api-key:}") String groqKey,
                        @Value("${app.assistant.groq.model}") String groqModel) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(timeout);
        List<OpenAiCompatibleClient> clients = Stream.of(
                        new LlmProvider("GEMINI", geminiUrl, geminiKey, geminiModel),
                        new LlmProvider("GROQ", groqUrl, groqKey, groqModel))
                .filter(LlmProvider::configured)
                .map(p -> new OpenAiCompatibleClient(p.name(), p.model(), builder.clone()
                        .baseUrl(p.baseUrl())
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + p.apiKey().trim())
                        .requestFactory(factory)
                        .build()))
                .toList();
        return new FallbackLlm(clients);
    }
}
