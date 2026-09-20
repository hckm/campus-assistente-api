package br.edu.usc.campusiachatbot.client;

import java.util.List;

public record AzureOpenAiChatResponse(
        String content,
        List<AzureOpenAiToolCall> toolCalls
) {

    public AzureOpenAiChatResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
