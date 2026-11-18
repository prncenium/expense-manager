package com.expensemanager.assistant.llm;

import java.util.Map;

/** A tool the model may call; {@code parameters} is a JSON schema object. */
public record ToolDef(String type, FunctionDef function) {

    public static ToolDef of(String name, String description, Map<String, Object> parameters) {
        return new ToolDef("function", new FunctionDef(name, description, parameters));
    }

    public record FunctionDef(String name, String description, Map<String, Object> parameters) {
    }
}
