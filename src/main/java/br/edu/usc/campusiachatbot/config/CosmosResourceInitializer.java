package br.edu.usc.campusiachatbot.config;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosContainerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
@ConditionalOnBean(CosmosClient.class)
@ConditionalOnProperty(prefix = "azure.cosmos.provisioning", name = "enabled", havingValue = "true")
public class CosmosResourceInitializer implements ApplicationRunner {

    private final CosmosClient client;
    private final CosmosProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (properties.getProvisioning().isResetOnStartup()) {
            resetDatabase();
        }
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

    private void resetDatabase() {
        if (!properties.getSeed().isEnabled()) {
            throw new IllegalStateException(
                    "Reset do Cosmos exige azure.cosmos.seed.enabled=true para nao recriar o database vazio"
            );
        }

        log.warn("Reset destrutivo habilitado: removendo o database Cosmos {}", properties.getDatabase());
        try {
            client.getDatabase(properties.getDatabase()).delete();
        } catch (CosmosException exception) {
            if (exception.getStatusCode() != 404) {
                throw exception;
            }
        }
    }
}
