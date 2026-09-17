package br.edu.usc.campusiachatbot.repository;

import br.edu.usc.campusiachatbot.config.CosmosProperties;
import br.edu.usc.campusiachatbot.domain.EstabelecimentoComercial;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CosmosEstabelecimentoComercialStoreTest {

    private CosmosContainer container;
    private CosmosEstabelecimentoComercialStore store;

    @BeforeEach
    void preparar() {
        CosmosClient client = mock(CosmosClient.class);
        CosmosDatabase database = mock(CosmosDatabase.class);
        container = mock(CosmosContainer.class);
        CosmosProperties properties = new CosmosProperties();
        properties.setDatabase("campus");
        properties.setEstabelecimentosContainer("estabelecimentos");
        when(client.getDatabase("campus")).thenReturn(database);
        when(database.getContainer("estabelecimentos")).thenReturn(container);
        store = new CosmosEstabelecimentoComercialStore(client, properties);
    }

    @Test
    void deveCriarDocumentoComDadosComerciaisEChaveDeParticaoDoTenant() {
        assertThat(store.criarSeAusente(estabelecimento())).isTrue();

        ArgumentCaptor<EstabelecimentoComercialDocument> captor =
                ArgumentCaptor.forClass(EstabelecimentoComercialDocument.class);
        ArgumentCaptor<PartitionKey> partitionKey = ArgumentCaptor.forClass(PartitionKey.class);
        verify(container).createItem(captor.capture(), partitionKey.capture(), any(CosmosItemRequestOptions.class));
        assertThat(captor.getValue().getId()).isEqualTo("renovo");
        assertThat(captor.getValue().getFormasPagamento()).isEqualTo("Pix e cartao");
        assertThat(captor.getValue().getCondicoesParcelamento()).isEqualTo("3 parcelas");
        assertThat(captor.getValue().getCidadesAtendidas()).containsExactly("Bauru");
        assertThat(partitionKey.getValue()).isEqualTo(new PartitionKey("renovo"));
    }

    @Test
    void deveTratarConflitoComoDocumentoExistenteSemAtualizaLo() {
        CosmosException conflito = mock(CosmosException.class);
        when(conflito.getStatusCode()).thenReturn(409);
        when(container.createItem(any(EstabelecimentoComercialDocument.class), any(PartitionKey.class),
                any(CosmosItemRequestOptions.class))).thenThrow(conflito);

        assertThat(store.criarSeAusente(estabelecimento())).isFalse();

        verify(container).createItem(any(EstabelecimentoComercialDocument.class), eq(new PartitionKey("renovo")),
                any(CosmosItemRequestOptions.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void deveRejeitarDocumentoComSchemaVersionAusenteOuDivergente(int schemaVersion) {
        EstabelecimentoComercialDocument document = documento(schemaVersion);
        CosmosItemResponse<EstabelecimentoComercialDocument> response = mock(CosmosItemResponse.class);
        when(response.getItem()).thenReturn(document);
        when(container.readItem("renovo", new PartitionKey("renovo"), EstabelecimentoComercialDocument.class))
                .thenReturn(response);

        assertThatThrownBy(() -> store.buscar("renovo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Documento de estabelecimento invalido no Cosmos");
    }

    private EstabelecimentoComercialDocument documento(int schemaVersion) {
        EstabelecimentoComercialDocument document = new EstabelecimentoComercialDocument();
        document.setId("renovo");
        document.setEstabelecimentoId("renovo");
        document.setTipo("estabelecimento");
        document.setSchemaVersion(schemaVersion);
        return document;
    }

    private EstabelecimentoComercial estabelecimento() {
        return new EstabelecimentoComercial(
                "renovo", "Renovo", "farmacia de manipulacao", "08:00 as 18:00", "Rua Um, 1",
                "Pix e cartao", "3 parcelas", "Entrega local", List.of("Bauru"), "SP", "14999999999",
                "https://www.renovomanipulacao.com.br/"
        );
    }
}
