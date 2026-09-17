package br.edu.usc.campusiachatbot.config;

import br.edu.usc.campusiachatbot.repository.CosmosCatalogoStore;
import com.azure.cosmos.CosmosClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "catalogo", name = "backend", havingValue = "COSMOS")
public class CosmosCatalogoStoreConfig {

    @Bean
    public CosmosCatalogoStore cosmosCatalogoStore(
            ObjectProvider<CosmosClient> clientProvider,
            ObjectProvider<CosmosProperties> propertiesProvider,
            ObjectMapper objectMapper,
            Environment environment
    ) {
        if (!environment.getProperty("azure.cosmos.enabled", Boolean.class, false)) {
            throw new IllegalStateException("catalogo.backend=COSMOS exige azure.cosmos.enabled=true");
        }
        CosmosClient client = clientProvider.getIfAvailable();
        CosmosProperties properties = propertiesProvider.getIfAvailable();
        if (client == null || properties == null) {
            throw new IllegalStateException("catalogo.backend=COSMOS exige azure.cosmos.enabled=true");
        }
        return new CosmosCatalogoStore(client, properties, objectMapper);
    }
}
