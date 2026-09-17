package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.config.CosmosProperties;
import br.edu.usc.campusiachatbot.config.CosmosEmulatorEndpointValidator;
import br.edu.usc.campusiachatbot.repository.CosmosCatalogoStore;
import br.edu.usc.campusiachatbot.repository.CosmosEstabelecimentoComercialStore;
import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "COSMOS_EMULATOR_TEST", matches = "true")
class CosmosCatalogoSeedIT {

    @Test
    void deveImportarCatalogoDeFormaRepetivel() {
        String endpoint = variavelObrigatoria("COSMOS_EMULATOR_ENDPOINT");
        String key = variavelObrigatoria("COSMOS_EMULATOR_KEY");
        CosmosEmulatorEndpointValidator.validar(endpoint, key);
        try (CosmosClient client = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .gatewayMode()
                .consistencyLevel(ConsistencyLevel.SESSION)
                .buildClient()) {
            CosmosProperties properties = new CosmosProperties();
            properties.setDatabase("campus-ia-chatbot");
            properties.setCatalogoContainer("catalogo");
            properties.setCatalogoId("renovo");
            properties.setEstabelecimentosContainer("estabelecimentos");
            properties.setEstabelecimentoId("renovo");
            client.createDatabaseIfNotExists(properties.getDatabase());
            var database = client.getDatabase(properties.getDatabase());
            database.createContainerIfNotExists(new com.azure.cosmos.models.CosmosContainerProperties(
                    properties.getCatalogoContainer(), "/catalogoId"
            ));
            database.createContainerIfNotExists(new com.azure.cosmos.models.CosmosContainerProperties(
                    properties.getEstabelecimentosContainer(), "/estabelecimentoId"
            ));
            CosmosCatalogoStore store = new CosmosCatalogoStore(client, properties, new ObjectMapper());
            CosmosEstabelecimentoComercialStore estabelecimentoStore =
                    new CosmosEstabelecimentoComercialStore(client, properties);
            CosmosSeedDataLoader loader = new CosmosSeedDataLoader(
                    store, estabelecimentoStore, properties, new ObjectMapper(), new DefaultResourceLoader()
            );

            loader.run(new DefaultApplicationArguments(new String[0]));
            loader.run(new DefaultApplicationArguments(new String[0]));

            assertThat(store.listarTodosOrdenadosPorCodigo())
                    .hasSize(19)
                    .extracting(produto -> produto.codigoCatalogo())
                    .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 19).boxed().toList());
            assertThat(estabelecimentoStore.buscar("renovo")).isPresent();
        }
    }

    private String variavelObrigatoria(String nome) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(nome + " e obrigatoria para o seed Cosmos");
        }
        return valor;
    }
}
