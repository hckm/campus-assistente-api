package br.edu.usc.campusiachatbot.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class PortalWebConfig implements WebMvcConfigurer {

    private final PortalCorsProperties properties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/portal/**")
                .allowedOrigins(properties.allowedOriginsArray())
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("Content-Type", "Idempotency-Key")
                .maxAge(3600);
    }
}
