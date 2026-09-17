package br.edu.usc.campusiachatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "portal.cors")
public record PortalCorsProperties(List<String> allowedOrigins) {

    public String[] allowedOriginsArray() {
        if (allowedOrigins == null) {
            return new String[0];
        }
        return allowedOrigins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(String::trim)
                .toArray(String[]::new);
    }
}
