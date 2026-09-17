package br.edu.usc.campusiachatbot.config;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AzureOpenAiPropertiesTest {

    @Test
    void deveMontarEndpointV1APartirDoEndpointDoRecurso() {
        AzureOpenAiProperties properties = properties("https://recurso.openai.azure.com", "deployment");

        properties.validarSelecionado();

        assertThat(properties.chatCompletionsUri())
                .isEqualTo(URI.create("https://recurso.openai.azure.com/openai/v1/chat/completions"));
    }

    @Test
    void deveAceitarEndpointQueJaContemOpenAiV1() {
        AzureOpenAiProperties properties = properties(
                "https://recurso.openai.azure.com/openai/v1/",
                "deployment"
        );

        properties.validarSelecionado();

        assertThat(properties.chatCompletionsUri())
                .isEqualTo(URI.create("https://recurso.openai.azure.com/openai/v1/chat/completions"));
    }

    @Test
    void deveRejeitarEndpointDeProjetoFoundry() {
        AzureOpenAiProperties properties = properties(
                "https://recurso.services.ai.azure.com/api/projects/projeto",
                "deployment"
        );

        assertThatThrownBy(properties::validarSelecionado)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("endpoint de inferencia Azure OpenAI");
    }

    @Test
    void deveRejeitarConfiguracaoSelecionadaSemDeployment() {
        AzureOpenAiProperties properties = properties("https://recurso.openai.azure.com", "");

        assertThatThrownBy(properties::validarSelecionado)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deployment");
    }

    @Test
    void deveRejeitarApiKeyVaziaSemExporValor() {
        AzureOpenAiProperties properties = new AzureOpenAiProperties(
                "https://recurso.openai.azure.com",
                "deployment",
                AzureOpenAiProperties.Authentication.API_KEY,
                "",
                "",
                Duration.ofSeconds(30),
                800,
                65536
        );

        assertThatThrownBy(properties::validarSelecionado)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("azure.openai.api-key e obrigatoria para autenticacao API_KEY");
    }

    private AzureOpenAiProperties properties(String endpoint, String deployment) {
        return new AzureOpenAiProperties(
                endpoint,
                deployment,
                AzureOpenAiProperties.Authentication.MANAGED_IDENTITY,
                "",
                "",
                Duration.ofSeconds(30),
                800,
                65536
        );
    }
}
