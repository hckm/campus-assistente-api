package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.config.CosmosProperties;
import br.edu.usc.campusiachatbot.domain.EstabelecimentoComercial;
import br.edu.usc.campusiachatbot.domain.ProdutoCatalogo;
import br.edu.usc.campusiachatbot.repository.CosmosCatalogoStore;
import br.edu.usc.campusiachatbot.store.EstabelecimentoComercialStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CosmosSeedDataLoaderTest {

    @Test
    void arquivoDeProducaoDeveConterFormasDePagamentoDaRenovo() throws Exception {
        try (var input = Objects.requireNonNull(
                getClass().getResourceAsStream("/data/estabelecimento-renovo.json")
        )) {
            EstabelecimentoComercial estabelecimento = new ObjectMapper().readValue(
                    input,
                    EstabelecimentoComercial.class
            );

            assertThat(estabelecimento.formasPagamento())
                    .isEqualTo("Pix, cartão de crédito e cartão de débito");
        }
    }

    @Test
    void deveCarregarEstabelecimentoECatalogoQuandoVazios() throws Exception {
        CosmosCatalogoStore catalogo = mock(CosmosCatalogoStore.class);
        EstabelecimentoComercialStore estabelecimento = mock(EstabelecimentoComercialStore.class);
        CosmosSeedDataLoader loader = loader(catalogo, estabelecimento, estabelecimentoJson(), catalogoCsv());

        loader.run(new DefaultApplicationArguments(new String[0]));

        verify(estabelecimento).criarSeAusente(any(EstabelecimentoComercial.class));
        verify(catalogo).criarTodosSeVazio(org.mockito.ArgumentMatchers.argThat(produtos -> produtos.size() == 2
                && produtos.stream().map(ProdutoCatalogo::codigoCatalogo).toList().equals(List.of(1, 2))));
        verify(catalogo, never()).salvarTodos(any());
    }

    @Test
    void deveExecutarCargaRepetidaSemSolicitarAtualizacao() throws Exception {
        CosmosCatalogoStore catalogo = mock(CosmosCatalogoStore.class);
        EstabelecimentoComercialStore estabelecimento = mock(EstabelecimentoComercialStore.class);
        when(estabelecimento.criarSeAusente(any())).thenReturn(false);
        when(catalogo.criarTodosSeVazio(any())).thenReturn(false);
        CosmosSeedDataLoader loader = loader(catalogo, estabelecimento, estabelecimentoJson(), catalogoCsv());

        loader.run(new DefaultApplicationArguments(new String[0]));
        loader.run(new DefaultApplicationArguments(new String[0]));

        verify(estabelecimento, org.mockito.Mockito.times(2)).criarSeAusente(any());
        verify(catalogo, org.mockito.Mockito.times(2)).criarTodosSeVazio(any());
        verify(catalogo, never()).salvarTodos(any());
    }

    @Test
    void deveFalharAntesDePersistirQuandoArquivoDeEstabelecimentoForInvalido() {
        CosmosCatalogoStore catalogo = mock(CosmosCatalogoStore.class);
        EstabelecimentoComercialStore estabelecimento = mock(EstabelecimentoComercialStore.class);
        CosmosSeedDataLoader loader = loader(catalogo, estabelecimento, "{", catalogoCsv());

        assertThatThrownBy(() -> loader.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falha ao carregar arquivo do estabelecimento");
        verifyNoInteractions(catalogo, estabelecimento);
    }

    @Test
    void deveFalharAntesDePersistirQuandoArquivoDeCatalogoForInvalido() {
        CosmosCatalogoStore catalogo = mock(CosmosCatalogoStore.class);
        EstabelecimentoComercialStore estabelecimento = mock(EstabelecimentoComercialStore.class);
        CosmosSeedDataLoader loader = loader(catalogo, estabelecimento, estabelecimentoJson(), "codigo,produto\n1,invalido");

        assertThatThrownBy(() -> loader.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Linha invalida no catalogo Renovo");
        verifyNoInteractions(catalogo, estabelecimento);
    }

    private CosmosSeedDataLoader loader(
            CosmosCatalogoStore catalogo,
            EstabelecimentoComercialStore estabelecimento,
            String estabelecimentoJson,
            String catalogoCsv
    ) {
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        when(resourceLoader.getResource("classpath:data/estabelecimento-renovo.json"))
                .thenReturn(resource(estabelecimentoJson));
        when(resourceLoader.getResource("classpath:data/catalogo-renovo.csv"))
                .thenReturn(resource(catalogoCsv));
        CosmosProperties properties = new CosmosProperties();
        properties.setEstabelecimentoId("renovo");
        return new CosmosSeedDataLoader(catalogo, estabelecimento, properties, new ObjectMapper(), resourceLoader);
    }

    private ByteArrayResource resource(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }

    private String estabelecimentoJson() {
        return """
                {"estabelecimentoId":"renovo","nome":"Renovo","tipo":"farmacia de manipulacao","horarioFuncionamento":"","endereco":"","formasPagamento":"Pix","condicoesParcelamento":"","entrega":"","cidadesAtendidas":["Bauru"],"uf":"SP","telefone":"","site":""}
                """;
    }

    private String catalogoCsv() {
        return """
                codigo,produto,descricao,categoria,precoAtual,precoOriginal,urlCatalogo
                1,Produto Um,Descricao,Categoria,10.00,,
                2,Produto Dois,Descricao,Categoria,20.00,25.00,https://catalogo.example/dois
                """;
    }
}
