package br.edu.usc.campusiachatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "codex-cli")
public record CodexCliProperties(
        String executable,
        String model,
        Duration timeout,
        int maxOutputBytes
) {

    public CodexCliProperties {
        executable = executable == null || executable.isBlank() ? "codex" : executable.trim();
        model = model == null ? "" : model.trim();
        timeout = timeout == null ? Duration.ofSeconds(45) : timeout;
        maxOutputBytes = maxOutputBytes <= 0 ? 65536 : maxOutputBytes;

        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("codex-cli.timeout deve ser positivo");
        }
    }

    public boolean hasModel() {
        return !model.isBlank();
    }
}
