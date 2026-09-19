package br.edu.usc.campusiachatbot.config;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
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
        if (properties.getAutenticacao() == CosmosProperties.Autenticacao.MANAGED_IDENTITY) {
            inicializarComIdentidadeGerenciada();
            return;
        }

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

    private void inicializarComIdentidadeGerenciada() {
        if (!properties.getProvisioning().isResetOnStartup()) {
            log.info("Provisionamento estrutural do Cosmos ignorado com identidade gerenciada; database e containers devem existir");
            return;
        }

        validarCargaInicialParaReset();
        CosmosDatabase database = client.getDatabase(properties.getDatabase());
        log.warn("Reset destrutivo habilitado: removendo todos os documentos dos containers Cosmos configurados");
        limparContainer(database, properties.getConversasContainer(), "clienteChave");
        limparContainer(database, properties.getCatalogoContainer(), "catalogoId");
        limparContainer(database, properties.getEstabelecimentosContainer(), "estabelecimentoId");
    }

    private void limparContainer(CosmosDatabase database, String containerId, String partitionKeyProperty) {
        CosmosContainer container = database.getContainer(containerId);
        String query = "SELECT c.id, c." + partitionKeyProperty + " AS partitionKey FROM c";
        int removidos = 0;
        for (ResetItem item : container.queryItems(query, new CosmosQueryRequestOptions(), ResetItem.class)) {
            container.deleteItem(
                    item.id(),
                    item.partitionKey() == null ? PartitionKey.NONE : new PartitionKey(item.partitionKey()),
                    new CosmosItemRequestOptions()
            );
            removidos++;
        }
        log.warn("Reset do Cosmos removeu {} documento(s) do container {}", removidos, containerId);
    }

    private void resetDatabase() {
        validarCargaInicialParaReset();

        log.warn("Reset destrutivo habilitado: removendo o database Cosmos {}", properties.getDatabase());
        try {
            client.getDatabase(properties.getDatabase()).delete();
        } catch (CosmosException exception) {
            if (exception.getStatusCode() != 404) {
                throw exception;
            }
        }
    }

    private void validarCargaInicialParaReset() {
        if (!properties.getSeed().isEnabled()) {
            throw new IllegalStateException(
                    "Reset do Cosmos exige azure.cosmos.seed.enabled=true para nao recriar os dados vazios"
            );
        }
    }

    public record ResetItem(String id, String partitionKey) {
    }
}
