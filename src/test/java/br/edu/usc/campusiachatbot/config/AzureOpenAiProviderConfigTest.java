package br.edu.usc.campusiachatbot.config;

import br.edu.usc.campusiachatbot.CampusIaChatbotApplication;
import br.edu.usc.campusiachatbot.service.AzureOpenAiInterpretacaoService;
import br.edu.usc.campusiachatbot.service.CatalogoInterpretacaoOrchestrator;
import br.edu.usc.campusiachatbot.service.InterpretacaoIaService;
import br.edu.usc.campusiachatbot.service.LocalInterpretacaoService;
import br.edu.usc.campusiachatbot.service.PromptBuilderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AzureOpenAiProviderConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(AzureOpenAiProviderConfig.class)
            .withBean(CatalogoInterpretacaoOrchestrator.class, () -> mock(CatalogoInterpretacaoOrchestrator.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void deveSelecionarAzureOpenAiSemExecutarChamadaExterna() {
        contextRunner.withPropertyValues(
                "ia.provider=AZURE_OPENAI",
                "azure.openai.endpoint=https://recurso.openai.azure.com",
                "azure.openai.deployment=deployment-teste",
                "azure.openai.authentication=API_KEY",
                "azure.openai.api-key=segredo-teste"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(InterpretacaoIaService.class))
                    .isInstanceOf(AzureOpenAiInterpretacaoService.class);
        });
    }

    @Test
    void deveSelecionarSomenteAzureOpenAiNaAplicacao() {
        try (var context = new SpringApplicationBuilder(CampusIaChatbotApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("local")
                .run(
                        "--ia.provider=AZURE_OPENAI",
                        "--azure.openai.endpoint=https://recurso.openai.azure.com",
                        "--azure.openai.deployment=deployment-teste",
                        "--azure.openai.authentication=API_KEY",
                        "--azure.openai.api-key=segredo-teste",
                        "--spring.datasource.url=jdbc:h2:mem:azure_openai_provider",
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--azure.cosmos.enabled=false",
                        "--cep-lookup.enabled=false"
                )) {
            assertThat(context.getBeansOfType(InterpretacaoIaService.class)).hasSize(1);
            assertThat(context.getBean(InterpretacaoIaService.class))
                    .isInstanceOf(AzureOpenAiInterpretacaoService.class);
        }
    }

    @Test
    void deveFalharClaramenteQuandoConfiguracaoSelecionadaForInvalida() {
        contextRunner.withPropertyValues(
                "ia.provider=AZURE_OPENAI",
                "azure.openai.endpoint=https://recurso.openai.azure.com",
                "azure.openai.authentication=MANAGED_IDENTITY"
        ).run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .hasRootCauseMessage("azure.openai.deployment deve identificar um deployment valido"));
    }

    @Test
    void naoDeveCriarAdaptadorAzureQuandoOutroProvedorForSelecionado() {
        contextRunner.withPropertyValues("ia.provider=GEMINI")
                .run(context -> assertThat(context).doesNotHaveBean(InterpretacaoIaService.class));
    }
}
