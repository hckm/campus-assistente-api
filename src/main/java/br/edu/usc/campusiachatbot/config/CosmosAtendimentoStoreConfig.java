package br.edu.usc.campusiachatbot.config;

import br.edu.usc.campusiachatbot.repository.CosmosAtendimentoStore;
import br.edu.usc.campusiachatbot.store.AtendimentoStore;
import com.azure.cosmos.CosmosClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "persistencia", name = "backend", havingValue = "COSMOS")
public class CosmosAtendimentoStoreConfig {

    @Bean
    public AtendimentoStore cosmosAtendimentoStore(
            ObjectProvider<CosmosClient> clientProvider,
            ObjectProvider<CosmosProperties> propertiesProvider,
            Environment environment
    ) {
        if (!environment.getProperty("azure.cosmos.enabled", Boolean.class, false)) {
            throw new IllegalStateException("persistencia.backend=COSMOS exige azure.cosmos.enabled=true");
        }
        CosmosClient client = clientProvider.getIfAvailable();
        CosmosProperties properties = propertiesProvider.getIfAvailable();
        if (client == null || properties == null) {
            throw new IllegalStateException("persistencia.backend=COSMOS exige azure.cosmos.enabled=true");
        }
        return new CosmosAtendimentoStore(client, properties);
    }
}
