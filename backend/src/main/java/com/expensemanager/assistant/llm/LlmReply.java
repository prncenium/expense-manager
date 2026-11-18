package com.expensemanager.assistant.llm;

/** The model's message plus which provider answered ("GEMINI" / "GROQ"). */
public record LlmReply(ChatMessage message, String provider) {
}
