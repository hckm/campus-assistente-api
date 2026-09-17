package br.edu.usc.campusiachatbot.repository;

import br.edu.usc.campusiachatbot.config.CosmosProperties;
import br.edu.usc.campusiachatbot.config.CosmosEmulatorEndpointValidator;
import br.edu.usc.campusiachatbot.domain.ProdutoCatalogo;
import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CompositePath;
import com.azure.cosmos.models.CompositePathSortOrder;
import com.azure.cosmos.models.CosmosBatch;
import com.azure.cosmos.models.CosmosBatchItemRequestOptions;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.PartitionKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "COSMOS_EMULATOR_TEST", matches = "true")
class CosmosCatalogoEmulatorIT {

    @Test
    void deveSuportarConflitoEtagEBatchCondicionalAtomico() {
        String endpoint = variavelObrigatoria("COSMOS_EMULATOR_ENDPOINT");
        String key = variavelObrigatoria("COSMOS_EMULATOR_KEY");
        CosmosEmulatorEndpointValidator.validar(endpoint, key);
        try (CosmosClient client = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .gatewayMode()
                .consistencyLevel(ConsistencyLevel.SESSION)
                .buildClient()) {
            CosmosContainer container = client.getDatabase("campus-ia-chatbot").getContainer("catalogo");
            String partition = "concorrencia:" + UUID.randomUUID();
            PartitionKey partitionKey = new PartitionKey(partition);
            String controlId = "controle:" + UUID.randomUUID();
            String markerId = "marcador:" + UUID.randomUUID();
            Map<String, Object> control = Map.of("id", controlId, "catalogoId", partition, "version", 1);
            Map<String, Object> updated = Map.of("id", controlId, "catalogoId", partition, "version", 2);
            Map<String, Object> marker = Map.of("id", markerId, "catalogoId", partition, "version", 1);

            try {
                var created = container.createItem(control, partitionKey, new CosmosItemRequestOptions());
                org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                                container.createItem(control, partitionKey, new CosmosItemRequestOptions()))
                        .isInstanceOf(CosmosException.class)
                        .extracting(exception -> ((CosmosException) exception).getStatusCode())
                        .isEqualTo(409);

                CosmosBatch successful = CosmosBatch.createCosmosBatch(partitionKey);
                successful.replaceItemOperation(controlId, updated, new CosmosBatchItemRequestOptions()
                        .setIfMatchETag(created.getETag()));
                successful.createItemOperation(marker);
                assertThat(container.executeCosmosBatch(successful).isSuccessStatusCode()).isTrue();

                CosmosBatch stale = CosmosBatch.createCosmosBatch(partitionKey);
                stale.replaceItemOperation(controlId, control, new CosmosBatchItemRequestOptions()
                        .setIfMatchETag(created.getETag()));
                stale.createItemOperation(Map.of(
                                "id", markerId + ":stale",
                                "catalogoId", partition,
                                "version", 1
                        ));
                assertThat(container.executeCosmosBatch(stale).isSuccessStatusCode()).isFalse();
                assertThat(container.readItem(controlId, partitionKey, Map.class).getItem())
                        .containsEntry("version", 2);
                org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                                container.readItem(markerId + ":stale", partitionKey, Map.class))
                        .isInstanceOf(CosmosException.class)
                        .extracting(exception -> ((CosmosException) exception).getStatusCode())
                        .isEqualTo(404);
            } finally {
                excluirSeExistir(container, controlId, partitionKey);
                excluirSeExistir(container, markerId, partitionKey);
                excluirSeExistir(container, markerId + ":stale", partitionKey);
            }
        }
    }

    @Test
    void deveProvarCatalogoRealNoEmulador() throws Exception {
        String endpoint = variavelObrigatoria("COSMOS_EMULATOR_ENDPOINT");
        String key = variavelObrigatoria("COSMOS_EMULATOR_KEY");
        CosmosEmulatorEndpointValidator.validar(endpoint, key);
        ObjectMapper objectMapper = new ObjectMapper();
        try (CosmosClient client = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .gatewayMode()
                .consistencyLevel(ConsistencyLevel.SESSION)
                .buildClient()) {
            client.createDatabaseIfNotExists("campus-ia-chatbot");
            var database = client.getDatabase("campus-ia-chatbot");
            CosmosContainerProperties catalogo = new CosmosContainerProperties("catalogo", "/catalogoId");
            catalogo.setIndexingPolicy(indexingPolicy(objectMapper));
            database.createContainerIfNotExists(catalogo);
            database.createContainerIfNotExists(new CosmosContainerProperties("conversas", "/clienteChave"));
            CosmosContainer container = database.getContainer("catalogo");
            assertThat(container.read().getProperties().getPartitionKeyDefinition().getPaths())
                    .containsExactly("/catalogoId");
            assertThat(database.getContainer("conversas").read().getProperties().getPartitionKeyDefinition().getPaths())
                    .containsExactly("/clienteChave");

            CosmosProperties properties = new CosmosProperties();
            properties.setDatabase("campus-ia-chatbot");
            properties.setCatalogoContainer("catalogo");
            properties.setCatalogoId("renovo");
            CosmosCatalogoStore store = new CosmosCatalogoStore(client, properties, objectMapper);
            int codigoBase = 1_000_000_000 + (UUID.randomUUID().hashCode() & 0x0fffffff);
            List<String> ids = List.of(
                    "produto:" + codigoBase,
                    "produto:" + (codigoBase + 1),
                    "produto:" + (codigoBase + 2)
            );
            String categoria = "PROVA " + UUID.randomUUID();

            try {
                ProdutoCatalogo salvo = store.salvar(produto(codigoBase, categoria, "Produto A", "10.10"));
                assertThat(salvo.id()).isEqualTo(ids.getFirst());
                assertThat(store.buscarPorCodigoCatalogo(codigoBase)).contains(salvo);

                store.salvarTodos(List.of(
                        produto(codigoBase + 1, categoria, "Produto B", "20.20"),
                        produto(codigoBase + 2, categoria, "Produto C", "30.30")
                ));

                assertThat(store.listarPorCategoriaOrdenadaPorProduto(categoria))
                        .extracting(ProdutoCatalogo::produto)
                        .containsExactly("Produto A", "Produto B", "Produto C");
                assertThat(store.listarPorFaixaDePrecoOrdenadaPorPreco(
                        new BigDecimal("20.00"),
                        new BigDecimal("31.00")
                )).extracting(ProdutoCatalogo::codigoCatalogo)
                        .contains(codigoBase + 1, codigoBase + 2);
            } finally {
                ids.forEach(id -> excluirSeExistir(container, id));
            }

            assertThat(ids).allSatisfy(id -> assertThat(store.buscarPorId(id)).isEmpty());
        }
    }

    private ProdutoCatalogo produto(int codigo, String categoria, String nome, String preco) {
        return new ProdutoCatalogo(
                null,
                null,
                codigo,
                categoria,
                nome,
                "Dado sintetico da prova local",
                new BigDecimal(preco),
                null,
                null
        );
    }

    private void excluirSeExistir(CosmosContainer container, String id) {
        excluirSeExistir(container, id, new PartitionKey("renovo"));
    }

    private void excluirSeExistir(CosmosContainer container, String id, PartitionKey partitionKey) {
        try {
            container.deleteItem(id, partitionKey, null);
        } catch (CosmosException exception) {
            if (exception.getStatusCode() != 404) {
                throw exception;
            }
        }
    }

    private IndexingPolicy indexingPolicy(ObjectMapper objectMapper) throws Exception {
        JsonNode root = objectMapper.readTree(
                Path.of("infra", "cosmos", "catalogo-indexing-policy.json").toFile()
        );
        IndexingPolicy policy = new IndexingPolicy()
                .setAutomatic(root.path("automatic").asBoolean())
                .setIndexingMode(IndexingMode.valueOf(root.path("indexingMode").asText().toUpperCase(Locale.ROOT)));
        List<IncludedPath> includedPaths = new ArrayList<>();
        root.path("includedPaths").forEach(item -> includedPaths.add(new IncludedPath(item.path("path").asText())));
        policy.setIncludedPaths(includedPaths);
        List<ExcludedPath> excludedPaths = new ArrayList<>();
        root.path("excludedPaths").forEach(item -> excludedPaths.add(new ExcludedPath(item.path("path").asText())));
        policy.setExcludedPaths(excludedPaths);
        List<List<CompositePath>> compositeIndexes = new ArrayList<>();
        root.path("compositeIndexes").forEach(index -> {
            List<CompositePath> paths = new ArrayList<>();
            index.forEach(item -> paths.add(new CompositePath()
                    .setPath(item.path("path").asText())
                    .setOrder(CompositePathSortOrder.valueOf(
                            item.path("order").asText().toUpperCase(Locale.ROOT)
                    ))));
            compositeIndexes.add(paths);
        });
        return policy.setCompositeIndexes(compositeIndexes);
    }

    private String variavelObrigatoria(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " e obrigatoria para o teste do emulador");
        }
        return value;
    }
}
