package br.edu.usc.campusiachatbot.config;

import br.edu.usc.campusiachatbot.CampusIaChatbotApplication;
import br.edu.usc.campusiachatbot.service.CodexCliInterpretacaoService;
import br.edu.usc.campusiachatbot.service.CodexCliProcessRunner;
import br.edu.usc.campusiachatbot.service.CatalogoInterpretacaoOrchestrator;
import br.edu.usc.campusiachatbot.service.InterpretacaoIaService;
import br.edu.usc.campusiachatbot.service.LocalInterpretacaoService;
import br.edu.usc.campusiachatbot.service.PromptBuilderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CodexCliProviderConfigTest {

    private final CodexCliProviderConfig config = new CodexCliProviderConfig();
    private final CodexCliProperties properties = new CodexCliProperties(
            "codex",
            "",
            Duration.ofSeconds(5),
            4096
    );

    @Test
    void deveSelecionarCodexCliSomenteNoPerfilLocal() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertThat(config.codexCliInterpretacaoService(
                environment,
                properties,
                mock(CodexCliProcessRunner.class),
                mock(CatalogoInterpretacaoOrchestrator.class),
                new ObjectMapper()
        )).isInstanceOf(CodexCliInterpretacaoService.class);
    }

    @Test
    void deveRejeitarCodexCliForaDoPerfilLocal() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> config.codexCliInterpretacaoService(
                environment,
                properties,
                mock(CodexCliProcessRunner.class),
                mock(CatalogoInterpretacaoOrchestrator.class),
                new ObjectMapper()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("perfil local");
    }

    @Test
    void deveIniciarAplicacaoLocalComCodexSelecionadoSemExecutarChamada() {
        try (var context = new SpringApplicationBuilder(CampusIaChatbotApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("local")
                .run(
                        "--ia.provider=CODEX_CLI",
                        "--spring.datasource.url=jdbc:h2:mem:codex_cli_provider",
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--azure.cosmos.enabled=false",
                        "--cep-lookup.enabled=false"
                )) {
            assertThat(context.getBean(InterpretacaoIaService.class))
                    .isInstanceOf(CodexCliInterpretacaoService.class);
        }
    }
}
