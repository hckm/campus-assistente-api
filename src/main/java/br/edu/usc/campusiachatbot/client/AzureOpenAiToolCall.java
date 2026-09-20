package br.edu.usc.campusiachatbot.client;

public record AzureOpenAiToolCall(
        String id,
        String name,
        String arguments
) {
}
