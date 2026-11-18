package com.expensemanager.assistant.llm;

import java.util.List;

/** What the assistant needs from a chat model. Tests swap in a scripted fake. */
public interface LlmClient {

    /** One model call. Throws {@link AssistantUnavailableException} when no provider can answer. */
    LlmReply chat(List<ChatMessage> messages, List<ToolDef> tools);

    /** Configured providers in the order they are tried; empty = assistant is off. */
    List<String> providers();
}
