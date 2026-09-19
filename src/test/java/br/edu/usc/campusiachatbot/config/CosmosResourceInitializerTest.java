package br.edu.usc.campusiachatbot.config;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosContainerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Test
    void deveRemoverDatabaseAntesDeRecriarQuandoResetEstiverHabilitado() throws Exception {
        CosmosClient client = mock(CosmosClient.class);
        CosmosDatabase database = mock(CosmosDatabase.class);
        CosmosProperties properties = properties();
        properties.getProvisioning().setResetOnStartup(true);
        properties.getSeed().setEnabled(true);
        when(client.getDatabase("campus")).thenReturn(database);

        new CosmosResourceInitializer(client, properties).run(new DefaultApplicationArguments(new String[0]));

        org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(client, database);
        ordem.verify(client).getDatabase("campus");
        ordem.verify(database).delete();
        ordem.verify(client).createDatabaseIfNotExists("campus");
        ordem.verify(client).getDatabase("campus");
    }

    @Test
    void deveIgnorarDatabaseAusenteDuranteReset() throws Exception {
        CosmosClient client = mock(CosmosClient.class);
        CosmosDatabase database = mock(CosmosDatabase.class);
        CosmosException naoEncontrado = mock(CosmosException.class);
        CosmosProperties properties = properties();
        properties.getProvisioning().setResetOnStartup(true);
        properties.getSeed().setEnabled(true);
        when(naoEncontrado.getStatusCode()).thenReturn(404);
        when(database.delete()).thenThrow(naoEncontrado);
        when(client.getDatabase("campus")).thenReturn(database);

        new CosmosResourceInitializer(client, properties).run(new DefaultApplicationArguments(new String[0]));

        verify(client).createDatabaseIfNotExists("campus");
    }

    @Test
    void deveRecusarResetSemCargaInicialHabilitada() {
        CosmosClient client = mock(CosmosClient.class);
        CosmosDatabase database = mock(CosmosDatabase.class);
        CosmosProperties properties = properties();
        properties.getProvisioning().setResetOnStartup(true);
        when(client.getDatabase("campus")).thenReturn(database);

        assertThatThrownBy(() -> new CosmosResourceInitializer(client, properties)
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("azure.cosmos.seed.enabled=true");

        verify(database, never()).delete();
        verify(client, never()).createDatabaseIfNotExists(any(String.class));
    }

    private CosmosProperties properties() {
        CosmosProperties properties = new CosmosProperties();
        properties.setDatabase("campus");
        properties.setConversasContainer("conversas");
        properties.setCatalogoContainer("catalogo");
        properties.setEstabelecimentosContainer("estabelecimentos");
        return properties;
    }
}
