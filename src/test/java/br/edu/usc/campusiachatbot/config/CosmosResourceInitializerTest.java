package br.edu.usc.campusiachatbot.config;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CosmosResourceInitializerTest {

    @Test
    void deveCriarDatabaseEContainersComAsChavesDeParticaoEsperadas() throws Exception {
        CosmosClient client = mock(CosmosClient.class);
        CosmosDatabase database = mock(CosmosDatabase.class);
        CosmosProperties properties = new CosmosProperties();
        properties.setDatabase("campus");
        properties.setConversasContainer("conversas");
        properties.setCatalogoContainer("catalogo");
        properties.setEstabelecimentosContainer("estabelecimentos");
        when(client.getDatabase("campus")).thenReturn(database);

        new CosmosResourceInitializer(client, properties).run(new DefaultApplicationArguments(new String[0]));

        verify(client).createDatabaseIfNotExists("campus");
        org.mockito.ArgumentCaptor<CosmosContainerProperties> captor =
                org.mockito.ArgumentCaptor.forClass(CosmosContainerProperties.class);
        verify(database, org.mockito.Mockito.times(3)).createContainerIfNotExists(captor.capture());
        assertThat(captor.getAllValues()).extracting(CosmosContainerProperties::getId)
                .containsExactly("conversas", "catalogo", "estabelecimentos");
        assertThat(captor.getAllValues()).extracting(item -> item.getPartitionKeyDefinition().getPaths().getFirst())
                .containsExactly("/clienteChave", "/catalogoId", "/estabelecimentoId");
    }
}
