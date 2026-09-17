package br.edu.usc.campusiachatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ia")
public record IaProperties(Provider provider) {

    public IaProperties {
        provider = provider == null ? Provider.GEMINI : provider;
    }

    public enum Provider {
        GEMINI,
        CODEX_CLI,
        AZURE_OPENAI
    }
}
