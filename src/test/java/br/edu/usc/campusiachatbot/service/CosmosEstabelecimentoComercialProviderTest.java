package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.config.CosmosProperties;
import br.edu.usc.campusiachatbot.domain.EstabelecimentoComercial;
import br.edu.usc.campusiachatbot.store.EstabelecimentoComercialStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CosmosEstabelecimentoComercialProviderTest {

    @Test
    void deveConverterDadosPersistidosEUsarCache() {
        EstabelecimentoComercialStore store = mock(EstabelecimentoComercialStore.class);
        CosmosProperties properties = new CosmosProperties();
        properties.setEstabelecimentoId("renovo");
        when(store.buscar("renovo")).thenReturn(Optional.of(new EstabelecimentoComercial(
                "renovo", "Renovo", "farmacia de manipulacao", "08:00 as 18:00", "Rua Um, 1",
                "Pix e cartao", "3 parcelas", "Entrega local", List.of("Bauru"), "SP", "14999999999",
                "https://www.renovomanipulacao.com.br/"
        )));

        CosmosEstabelecimentoComercialProvider provider = new CosmosEstabelecimentoComercialProvider(store, properties);

        assertThat(provider.obter().formasPagamento()).isEqualTo("Pix e cartao");
        assertThat(provider.obter().condicoesParcelamento()).isEqualTo("3 parcelas");
        assertThat(provider.obter().cidadesAtendidas()).containsExactly("Bauru");
        verify(store).buscar("renovo");
    }
}
