package br.edu.usc.campusiachatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cep-lookup")
public record CepLookupProperties(
        Boolean enabled,
        String baseUrl,
        Integer timeoutSeconds
) {
    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public String resolvedBaseUrl() {
        return baseUrl == null || baseUrl.isBlank() ? "https://viacep.com.br" : baseUrl.trim();
    }

    public int resolvedTimeoutSeconds() {
        return timeoutSeconds == null || timeoutSeconds <= 0 ? 5 : timeoutSeconds;
    }
}
