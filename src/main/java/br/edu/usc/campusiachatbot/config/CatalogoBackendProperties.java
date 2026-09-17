package br.edu.usc.campusiachatbot.config;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "catalogo")
public class CatalogoBackendProperties {

    @NotNull
    private Backend backend = Backend.JPA;

    public enum Backend {
        JPA,
        COSMOS
    }
}
