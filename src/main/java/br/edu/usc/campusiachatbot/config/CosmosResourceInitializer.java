package br.edu.usc.campusiachatbot.config;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
@RequiredArgsConstructor
@ConditionalOnBean(CosmosClient.class)
@ConditionalOnProperty(prefix = "azure.cosmos.provisioning", name = "enabled", havingValue = "true")
public class CosmosResourceInitializer implements ApplicationRunner {

    private final CosmosClient client;
    private final CosmosProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        client.createDatabaseIfNotExists(properties.getDatabase());
        CosmosDatabase database = client.getDatabase(properties.getDatabase());
        database.createContainerIfNotExists(new CosmosContainerProperties(
                properties.getConversasContainer(), "/clienteChave"
        ));
        database.createContainerIfNotExists(new CosmosContainerProperties(
                properties.getCatalogoContainer(), "/catalogoId"
        ));
        database.createContainerIfNotExists(new CosmosContainerProperties(
                properties.getEstabelecimentosContainer(), "/estabelecimentoId"
        ));
    }
}
