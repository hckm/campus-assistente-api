package br.edu.usc.campusiachatbot.config;

import br.edu.usc.campusiachatbot.client.AzureOpenAiApiKeyAuthentication;
import br.edu.usc.campusiachatbot.client.AzureOpenAiAuthentication;
import br.edu.usc.campusiachatbot.client.AzureOpenAiClient;
import br.edu.usc.campusiachatbot.client.AzureOpenAiManagedIdentityAuthentication;
import br.edu.usc.campusiachatbot.service.AzureOpenAiInterpretacaoService;
import br.edu.usc.campusiachatbot.service.CatalogoInterpretacaoOrchestrator;
import br.edu.usc.campusiachatbot.service.InterpretacaoIaService;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AzureOpenAiProperties.class)
@ConditionalOnProperty(prefix = "ia", name = "provider", havingValue = "AZURE_OPENAI")
public class AzureOpenAiProviderConfig {

    @Bean
    InterpretacaoIaService azureOpenAiInterpretacaoService(
            AzureOpenAiProperties properties,
            CatalogoInterpretacaoOrchestrator orchestrator,
            ObjectMapper objectMapper
    ) {
        properties.validarSelecionado();
        AzureOpenAiAuthentication authentication = criarAutenticacao(properties);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        AzureOpenAiClient client = new AzureOpenAiClient(
                httpClient,
                properties.chatCompletionsUri(),
                properties.deployment(),
                properties.timeout(),
                properties.maxOutputTokens(),
                properties.maxResponseBytes(),
                authentication,
                objectMapper
        );
        return new AzureOpenAiInterpretacaoService(
                client,
                orchestrator
        );
    }

    private AzureOpenAiAuthentication criarAutenticacao(AzureOpenAiProperties properties) {
        if (properties.authentication() == AzureOpenAiProperties.Authentication.API_KEY) {
            return new AzureOpenAiApiKeyAuthentication(properties.apiKey());
        }

        ManagedIdentityCredentialBuilder credentialBuilder = new ManagedIdentityCredentialBuilder();
        if (properties.hasManagedIdentityClientId()) {
            credentialBuilder.clientId(properties.managedIdentityClientId());
        }
        return new AzureOpenAiManagedIdentityAuthentication(
                credentialBuilder.build(),
                properties.timeout()
        );
    }
}
