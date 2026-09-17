package br.edu.usc.campusiachatbot.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties
@ConditionalOnProperty(prefix = "azure.cosmos", name = "enabled", havingValue = "true")
public class CosmosConfig {

    @Bean
    @ConfigurationProperties(prefix = "azure.cosmos")
    public CosmosProperties cosmosProperties() {
        return new CosmosProperties();
    }

    @Bean(destroyMethod = "close")
    public CosmosClient cosmosClient(CosmosProperties properties, Environment environment) {
        if (properties.getAutenticacao() == CosmosProperties.Autenticacao.EMULATOR) {
            validarEmulador(properties, environment);
            return new CosmosClientBuilder()
                    .endpoint(properties.endpointUri().toString())
                    .key(properties.getEmulatorKey())
                    .gatewayMode()
                    .endpointDiscoveryEnabled(false)
                    .consistencyLevel(ConsistencyLevel.SESSION)
                    .buildClient();
        }

        if (StringUtils.hasText(properties.getEmulatorKey())) {
            throw new IllegalStateException("emulator-key somente pode ser usada com autenticacao EMULATOR");
        }

        ManagedIdentityCredentialBuilder credentialBuilder = new ManagedIdentityCredentialBuilder();
        if (StringUtils.hasText(properties.getManagedIdentityClientId())) {
            credentialBuilder.clientId(properties.getManagedIdentityClientId());
        }

        return new CosmosClientBuilder()
                .endpoint(properties.endpointUri().toString())
                .credential(credentialBuilder.build())
                .directMode()
                .consistencyLevel(ConsistencyLevel.SESSION)
                .buildClient();
    }

    private void validarEmulador(CosmosProperties properties, Environment environment) {
        if (!environment.matchesProfiles("local") || environment.matchesProfiles("prod")) {
            throw new IllegalStateException("Autenticacao EMULATOR exige perfil local e nao permite perfil prod");
        }
        CosmosEmulatorEndpointValidator.validar(properties.getEndpoint(), properties.getEmulatorKey());
        if (StringUtils.hasText(properties.getManagedIdentityClientId())) {
            throw new IllegalStateException("Autenticacao EMULATOR nao permite managed-identity-client-id");
        }
    }
}
