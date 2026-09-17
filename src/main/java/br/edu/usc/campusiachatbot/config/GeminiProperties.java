package br.edu.usc.campusiachatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
        String apiKey,
        String model,
        String baseUrl,
        Integer timeoutSeconds
) {
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public int resolvedTimeoutSeconds() {
        return timeoutSeconds == null || timeoutSeconds <= 0 ? 30 : timeoutSeconds;
    }
}
