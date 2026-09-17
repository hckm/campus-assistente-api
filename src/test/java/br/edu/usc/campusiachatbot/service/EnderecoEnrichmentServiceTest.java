package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.client.CepLookupClient;
import br.edu.usc.campusiachatbot.config.EstabelecimentoProperties;
import br.edu.usc.campusiachatbot.dto.CepLookupResponseDTO;
import br.edu.usc.campusiachatbot.dto.EnderecoEnriquecidoDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class EnderecoEnrichmentServiceTest {

    private final CepLookupClient cepLookupClient = mock(CepLookupClient.class);

    private final EstabelecimentoProperties properties = new EstabelecimentoProperties(
            "Farmacia Teste",
            "farmacia",
            "08:00-18:00",
            "Rua Teste, 123",
            "Pix",
            null,
            null,
            List.of("Iacanga", "Itaju"),
            "SP",
            "(14) 99999-9999",
            null
    );

    private final EnderecoEnrichmentService service = new EnderecoEnrichmentService(cepLookupClient, () -> properties);

    @Test
    void deveRetornarVazioQuandoMensagemNaoTemCepNemEndereco() {
        EnderecoEnriquecidoDTO resultado = service.enriquecer("Qual o horario?");

        assertThat(resultado.encontrado()).isFalse();
        verifyNoInteractions(cepLookupClient);
    }

    @Test
    void deveEnriquecerComDadosDoCepQuandoCepEncontrado() {
        when(cepLookupClient.consultar("17180959")).thenReturn(Optional.of(
                new CepLookupResponseDTO("17180-959", "Iacanga", "SP", false)
        ));

        EnderecoEnriquecidoDTO resultado = service.enriquecer("Entrega no CEP 17180-959");

        assertThat(resultado.temCidade()).isTrue();
        assertThat(resultado.cidade()).isEqualTo("Iacanga");
        assertThat(resultado.uf()).isEqualTo("SP");
        assertThat(resultado.cep()).isEqualTo("17180-959");
    }

    @Test
    void deveRetornarVazioQuandoCepNaoEncontrado() {
        when(cepLookupClient.consultar("99999999")).thenReturn(Optional.empty());

        EnderecoEnriquecidoDTO resultado = service.enriquecer("Entrego no CEP 99999-999");

        assertThat(resultado.encontrado()).isFalse();
    }

    @Test
    void deveEnriquecerComBuscaPorEnderecoQuandoLogradouroECidadeAtendidaSaoIdentificados() {
        when(cepLookupClient.buscarPorEndereco("SP", "Iacanga", "Rua Bauru")).thenReturn(List.of(
                new CepLookupResponseDTO("17180-959", "Rua Bauru", "", "Centro", "Iacanga", "SP", false)
        ));

        EnderecoEnriquecidoDTO resultado = service.enriquecer("Entrega na Rua Bauru 123 em Iacanga");

        assertThat(resultado.temCidade()).isTrue();
        assertThat(resultado.cidade()).isEqualTo("Iacanga");
    }

    @Test
    void deveRetornarVazioQuandoCidadeNaoAtendida() {
        EnderecoEnriquecidoDTO resultado = service.enriquecer("Entrega na Rua Principal em Bauru");

        assertThat(resultado.encontrado()).isFalse();
    }

    @Test
    void deveRetornarVazioQuandoMensagemNula() {
        EnderecoEnriquecidoDTO resultado = service.enriquecer(null);

        assertThat(resultado.encontrado()).isFalse();
        verifyNoInteractions(cepLookupClient);
    }

    @Test
    void deveNormalizarTipoAvenidaNoLogradouro() {
        when(cepLookupClient.buscarPorEndereco("SP", "Iacanga", "Avenida Brasil")).thenReturn(List.of(
                new CepLookupResponseDTO("17180-100", "Avenida Brasil", "", "Centro", "Iacanga", "SP", false)
        ));

        EnderecoEnriquecidoDTO resultado = service.enriquecer("Entrega na Av. Brasil 200 em Iacanga");

        assertThat(resultado.temCidade()).isTrue();
        verify(cepLookupClient).buscarPorEndereco("SP", "Iacanga", "Avenida Brasil");
    }

    @Test
    void deveIgnorarBuscaPorEnderecoQuandoLogradouroMenorQue3Chars() {
        EnderecoEnriquecidoDTO resultado = service.enriquecer("Rua Iacanga");

        assertThat(resultado.encontrado()).isFalse();
    }

    @Test
    void deveRetornarVazioQuandoBuscaPorEnderecoNaoRetornaCidadeAtendida() {
        when(cepLookupClient.buscarPorEndereco("SP", "Iacanga", "Rua Bauru")).thenReturn(List.of(
                new CepLookupResponseDTO("17000-000", "Rua Bauru", "", "Centro", "Botucatu", "SP", false)
        ));

        EnderecoEnriquecidoDTO resultado = service.enriquecer("Entrega na Rua Bauru 456 em Iacanga");

        assertThat(resultado.encontrado()).isFalse();
    }
}
