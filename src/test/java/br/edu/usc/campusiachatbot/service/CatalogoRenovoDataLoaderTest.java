package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.domain.ProdutoCatalogo;
import br.edu.usc.campusiachatbot.store.CatalogoStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CatalogoRenovoDataLoaderTest {

    private final CatalogoStore catalogoStore = mock(CatalogoStore.class);
    private final ResourceLoader resourceLoader = mock(ResourceLoader.class);
    private final CatalogoRenovoDataLoader loader = new CatalogoRenovoDataLoader(catalogoStore, resourceLoader);

    @Test
    void deveIgnorarQuandoArquivoNaoExiste() throws Exception {
        Resource resource = mock(Resource.class);
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(false);

        loader.run(null);

        verifyNoMoreInteractions(catalogoStore);
    }

    @Test
    void deveCarregarDoisProdutosDoArquivoCsv() throws Exception {
        String csv = "codigo,produto,descricao,categoria,precoAtual,precoOriginal,urlCatalogo\n"
                + "1,Produto A,Descricao A,CAT_A,10.00,15.00,https://site.com/a\n"
                + "2,Produto B,Descricao B,CAT_B,20.00,,\n";

        mockResource(csv);
        when(catalogoStore.buscarPorCodigoCatalogo(1)).thenReturn(Optional.empty());
        when(catalogoStore.buscarPorCodigoCatalogo(2)).thenReturn(Optional.empty());

        loader.run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProdutoCatalogo>> captor = ArgumentCaptor.captor();
        verify(catalogoStore).salvarTodos(captor.capture());

        List<ProdutoCatalogo> salvos = captor.getValue();
        assertThat(salvos).hasSize(2);
        assertThat(salvos.get(0).produto()).isEqualTo("Produto A");
        assertThat(salvos.get(0).precoAtual()).isEqualByComparingTo("10.00");
        assertThat(salvos.get(0).precoOriginal()).isEqualByComparingTo("15.00");
        assertThat(salvos.get(0).urlCatalogo()).isEqualTo("https://site.com/a");
        assertThat(salvos.get(1).precoOriginal()).isNull();
        assertThat(salvos.get(1).urlCatalogo()).isNull();
    }

    @Test
    void deveAtualizarEntidadeExistenteAoInvesDeInserir() throws Exception {
        String csv = "codigo,produto,descricao,categoria,precoAtual,precoOriginal,urlCatalogo\n"
                + "1,Nome Novo,Desc Nova,CAT,12.00,,\n";

        ProdutoCatalogo existente = new ProdutoCatalogo(
                "91",
                null,
                1,
                "CAT",
                "Nome Antigo",
                "Desc antiga",
                new BigDecimal("10.00"),
                null,
                null
        );

        mockResource(csv);
        when(catalogoStore.buscarPorCodigoCatalogo(1)).thenReturn(Optional.of(existente));

        loader.run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProdutoCatalogo>> captor = ArgumentCaptor.captor();
        verify(catalogoStore).salvarTodos(captor.capture());

        assertThat(captor.getValue().get(0).produto()).isEqualTo("Nome Novo");
        assertThat(captor.getValue().get(0).id()).isEqualTo("91");
        assertThat(captor.getValue().get(0).codigoCatalogo()).isEqualTo(1);
    }

    @Test
    void deveParsearValorEntreAspasComVirgula() throws Exception {
        String csv = "codigo,produto,descricao,categoria,precoAtual,precoOriginal,urlCatalogo\n"
                + "1,\"Produto, especial\",Descricao,CAT,10.00,,\n";

        mockResource(csv);
        when(catalogoStore.buscarPorCodigoCatalogo(1)).thenReturn(Optional.empty());

        loader.run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProdutoCatalogo>> captor = ArgumentCaptor.captor();
        verify(catalogoStore).salvarTodos(captor.capture());

        assertThat(captor.getValue().get(0).produto()).isEqualTo("Produto, especial");
    }

    @Test
    void deveLancarExcecaoParaLinhaComQuantidadeInvalidaDeColunas() throws Exception {
        String csv = "codigo,produto,descricao,categoria,precoAtual,precoOriginal,urlCatalogo\n"
                + "1,Produto A,apenas tres colunas\n";

        mockResource(csv);
        when(catalogoStore.buscarPorCodigoCatalogo(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loader.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Linha invalida");
    }

    @Test
    void deveIgnorarLinhasEmBranco() throws Exception {
        String csv = "codigo,produto,descricao,categoria,precoAtual,precoOriginal,urlCatalogo\n"
                + "\n"
                + "1,Produto A,Descricao A,CAT,10.00,,\n"
                + "\n";

        mockResource(csv);
        when(catalogoStore.buscarPorCodigoCatalogo(1)).thenReturn(Optional.empty());

        loader.run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProdutoCatalogo>> captor = ArgumentCaptor.captor();
        verify(catalogoStore).salvarTodos(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    private void mockResource(String csvContent) throws IOException {
        Resource resource = mock(Resource.class);
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(
                new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8))
        );
    }
}
