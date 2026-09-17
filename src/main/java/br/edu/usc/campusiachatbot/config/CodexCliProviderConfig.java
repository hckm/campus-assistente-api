package br.edu.usc.campusiachatbot.config;

import br.edu.usc.campusiachatbot.service.CodexCliExecutor;
import br.edu.usc.campusiachatbot.service.CodexCliInterpretacaoService;
import br.edu.usc.campusiachatbot.service.CodexCliProcessRunner;
import br.edu.usc.campusiachatbot.service.CatalogoInterpretacaoOrchestrator;
import br.edu.usc.campusiachatbot.service.InterpretacaoIaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
@ConditionalOnProperty(prefix = "ia", name = "provider", havingValue = "CODEX_CLI")
public class CodexCliProviderConfig {

    @Bean
    InterpretacaoIaService codexCliInterpretacaoService(
            Environment environment,
            CodexCliProperties properties,
            CodexCliProcessRunner processRunner,
            CatalogoInterpretacaoOrchestrator orchestrator,
            ObjectMapper objectMapper
    ) {
        if (!environment.acceptsProfiles(Profiles.of("local"))) {
            throw new IllegalStateException("O provedor CODEX_CLI so pode ser usado no perfil local");
        }

        return new CodexCliInterpretacaoService(
                new CodexCliExecutor(properties, processRunner),
                orchestrator,
                objectMapper
        );
    }
}
