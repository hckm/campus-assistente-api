package br.edu.usc.campusiachatbot.repository;

import br.edu.usc.campusiachatbot.config.CosmosProperties;
import br.edu.usc.campusiachatbot.domain.ProdutoCatalogo;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosBatch;
import com.azure.cosmos.models.CosmosBatchOperationResult;
import com.azure.cosmos.models.CosmosBatchResponse;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CosmosCatalogoStoreTest {

    private CosmosContainer container;
    private CosmosProperties properties;
    private CosmosCatalogoStore store;

    @BeforeEach
    void configurar() {
        CosmosClient client = mock(CosmosClient.class);
        CosmosDatabase database = mock(CosmosDatabase.class);
        container = mock(CosmosContainer.class);
        properties = new CosmosProperties();
        properties.setDatabase("database-teste");
        properties.setCatalogoContainer("catalogo-teste");
        properties.setCatalogoId("renovo");
        when(client.getDatabase("database-teste")).thenReturn(database);
        when(database.getContainer("catalogo-teste")).thenReturn(container);
        store = new CosmosCatalogoStore(client, properties, new ObjectMapper());
    }

    @Test
    void deveSalvarDocumentoDeterministicoComCentavosExatosENormalizacaoPreservandoAcentos() {
        CosmosItemResponse<CatalogoProdutoDocument> response = mock(CosmosItemResponse.class);
        when(response.getStatusCode()).thenReturn(200);
        when(container.upsertItem(any(CatalogoProdutoDocument.class), any(PartitionKey.class),
                any(CosmosItemRequestOptions.class))).thenReturn(response);

        ProdutoCatalogo salvo = store.salvar(produto("91", null, 7, "SÉRUM", "ÓLEO ÁUREO", "19.90"));

        ArgumentCaptor<CatalogoProdutoDocument> documentCaptor = ArgumentCaptor.forClass(CatalogoProdutoDocument.class);
        ArgumentCaptor<PartitionKey> partitionCaptor = ArgumentCaptor.forClass(PartitionKey.class);
        verify(container).upsertItem(documentCaptor.capture(), partitionCaptor.capture(),
                any(CosmosItemRequestOptions.class));
        CatalogoProdutoDocument document = documentCaptor.getValue();
        assertThat(document.getId()).isEqualTo("produto:7");
        assertThat(document.getLegacyId()).isEqualTo("91");
        assertThat(document.getCatalogoId()).isEqualTo("renovo");
        assertThat(document.getTipo()).isEqualTo("produto");
        assertThat(document.getSchemaVersion()).isEqualTo(1);
        assertThat(document.getCategoriaNormalizada()).isEqualTo("sérum");
        assertThat(document.getProdutoNormalizado()).isEqualTo("óleo áureo");
        assertThat(document.getPrecoAtualCentavos()).isEqualTo(1990L);
        assertThat(partitionCaptor.getValue()).isEqualTo(new PartitionKey("renovo"));
        assertThat(salvo.id()).isEqualTo("produto:7");
        assertThat(salvo.legacyId()).isEqualTo("91");
        assertThat(salvo.precoAtual()).isEqualByComparingTo("19.90");
    }

    @Test
    void deveRejeitarIdentidadeDivergenteAntesDeAcessarCosmos() {
        ProdutoCatalogo produto = produto("produto:8", null, 7, "Categoria", "Produto", "10.00");

        assertThatThrownBy(() -> store.salvar(produto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("diverge");
        verify(container, never()).upsertItem(any(), any(), any());
    }

    @Test
    void deveRejeitarPrecoComFracaoDeCentavoOuOverflowAntesDeAcessarCosmos() {
        ProdutoCatalogo fracionado = produto(null, null, 1, "Categoria", "Produto", "10.001");
        ProdutoCatalogo overflow = produto(null, null, 2, "Categoria", "Produto", "92233720368547758.08");

        assertThatThrownBy(() -> store.salvar(fracionado))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("centavos");
        assertThatThrownBy(() -> store.salvar(overflow))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("centavos");
        verify(container, never()).upsertItem(any(), any(), any());
    }

    @Test
    void deveExecutarTodoLoteEmUmUnicoBatchEValidarCadaResultado() {
        CosmosBatchOperationResult primeiroResultado = mock(CosmosBatchOperationResult.class);
        CosmosBatchOperationResult segundoResultado = mock(CosmosBatchOperationResult.class);
        when(primeiroResultado.isSuccessStatusCode()).thenReturn(true);
        when(segundoResultado.isSuccessStatusCode()).thenReturn(true);
        CosmosBatchResponse response = mock(CosmosBatchResponse.class);
        when(response.isSuccessStatusCode()).thenReturn(true);
        when(response.getResults()).thenReturn(List.of(primeiroResultado, segundoResultado));
        when(container.executeCosmosBatch(any(CosmosBatch.class))).thenReturn(response);

        store.salvarTodos(List.of(
                produto(null, null, 1, "Categoria", "Primeiro", "10.00"),
                produto(null, null, 2, "Categoria", "Segundo", "20.00")
        ));

        ArgumentCaptor<CosmosBatch> batchCaptor = ArgumentCaptor.forClass(CosmosBatch.class);
        verify(container).executeCosmosBatch(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getOperations()).hasSize(2);
        assertThat(batchCaptor.getValue().getPartitionKeyValue()).isEqualTo(new PartitionKey("renovo"));
        verify(primeiroResultado).isSuccessStatusCode();
        verify(segundoResultado).isSuccessStatusCode();
    }

    @Test
    void deveCriarCargaInicialSomenteQuandoCatalogoEstiverVazio() {
        CosmosPagedIterable<CatalogoProdutoDocument> vazio = mock(CosmosPagedIterable.class);
        when(vazio.iterableByPage()).thenReturn(List.of());
        when(container.queryItems(any(SqlQuerySpec.class), any(CosmosQueryRequestOptions.class),
                eq(CatalogoProdutoDocument.class))).thenReturn(vazio);
        CosmosBatchOperationResult resultado = mock(CosmosBatchOperationResult.class);
        when(resultado.isSuccessStatusCode()).thenReturn(true);
        CosmosBatchResponse response = mock(CosmosBatchResponse.class);
        when(response.isSuccessStatusCode()).thenReturn(true);
        when(response.getResults()).thenReturn(List.of(resultado));
        when(container.executeCosmosBatch(any(CosmosBatch.class))).thenReturn(response);

        assertThat(store.criarTodosSeVazio(List.of(produto(null, null, 1, "Categoria", "Produto", "10.00"))))
                .isTrue();

        verify(container).executeCosmosBatch(any(CosmosBatch.class));
    }

    @Test
    void deveNaoSobrescreverCatalogoExistenteNaCargaInicial() {
        CosmosPagedIterable<CatalogoProdutoDocument> existente = mock(CosmosPagedIterable.class);
        FeedResponse<CatalogoProdutoDocument> pagina = mock(FeedResponse.class);
        when(pagina.getResults()).thenReturn(List.of(documento(1, "Categoria", "Produto", 1000L)));
        when(existente.iterableByPage()).thenReturn(List.of(pagina));
        when(container.queryItems(any(SqlQuerySpec.class), any(CosmosQueryRequestOptions.class),
                eq(CatalogoProdutoDocument.class))).thenReturn(existente);

        assertThat(store.criarTodosSeVazio(List.of(produto(null, null, 2, "Categoria", "Novo", "20.00"))))
                .isFalse();

        verify(container, never()).executeCosmosBatch(any(CosmosBatch.class));
    }

    @Test
    void deveTratarConflitoConcorrenteComoCargaJaRealizada() {
        CosmosPagedIterable<CatalogoProdutoDocument> vazio = mock(CosmosPagedIterable.class);
        when(vazio.iterableByPage()).thenReturn(List.of());
        CosmosPagedIterable<CatalogoProdutoDocument> existente = mock(CosmosPagedIterable.class);
        FeedResponse<CatalogoProdutoDocument> pagina = mock(FeedResponse.class);
        when(pagina.getResults()).thenReturn(List.of(documento(1, "Categoria", "Produto", 1000L)));
        when(existente.iterableByPage()).thenReturn(List.of(pagina));
        when(container.queryItems(any(SqlQuerySpec.class), any(CosmosQueryRequestOptions.class),
                eq(CatalogoProdutoDocument.class))).thenReturn(vazio, existente);
        CosmosBatchOperationResult conflito = mock(CosmosBatchOperationResult.class);
        when(conflito.getStatusCode()).thenReturn(409);
        CosmosBatchResponse response = mock(CosmosBatchResponse.class);
        when(response.getResults()).thenReturn(List.of(conflito));
        when(container.executeCosmosBatch(any(CosmosBatch.class))).thenReturn(response);

        assertThat(store.criarTodosSeVazio(List.of(produto(null, null, 1, "Categoria", "Produto", "10.00"))))
                .isFalse();
    }

    @Test
    void deveRejeitarLoteDuplicadoOuAcimaDosLimitesAntesDeExecutarBatch() {
        ProdutoCatalogo primeiro = produto(null, null, 1, "Categoria", "Primeiro", "10.00");
        ProdutoCatalogo duplicado = produto(null, null, 1, "Categoria", "Segundo", "20.00");

        assertThatThrownBy(() -> store.salvarTodos(List.of(primeiro, duplicado)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicado");

        properties.setCatalogoBatchMaxOperations(1);
        assertThatThrownBy(() -> store.salvarTodos(List.of(primeiro, produto(null, null, 2,
                "Categoria", "Segundo", "20.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operacoes");

        properties.setCatalogoBatchMaxOperations(100);
        properties.setCatalogoBatchMaxBytes(1);
        assertThatThrownBy(() -> store.salvarTodos(List.of(primeiro)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bytes UTF-8");
        verify(container, never()).executeCosmosBatch(any(CosmosBatch.class));
    }

    @Test
    void devePropagarFalhaGlobalOuIndividualDoBatch() {
        CosmosBatchResponse global = mock(CosmosBatchResponse.class);
        when(global.isSuccessStatusCode()).thenReturn(false);
        when(global.getStatusCode()).thenReturn(429);
        when(container.executeCosmosBatch(any(CosmosBatch.class))).thenReturn(global);

        assertThatThrownBy(() -> store.salvarTodos(List.of(
                produto(null, null, 1, "Categoria", "Produto", "10.00"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("status=429");

        CosmosBatchOperationResult falha = mock(CosmosBatchOperationResult.class);
        when(falha.isSuccessStatusCode()).thenReturn(false);
        when(falha.getStatusCode()).thenReturn(409);
        CosmosBatchResponse individual = mock(CosmosBatchResponse.class);
        when(individual.isSuccessStatusCode()).thenReturn(true);
        when(individual.getResults()).thenReturn(List.of(falha));
        when(container.executeCosmosBatch(any(CosmosBatch.class))).thenReturn(individual);

        assertThatThrownBy(() -> store.salvarTodos(List.of(
                produto(null, null, 2, "Categoria", "Produto", "10.00"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("indice=0")
                .hasMessageContaining("status=409");
    }

    @Test
    void deveRetornarVazioSomenteParaLeitura404EPropagarOutrosErros() {
        CosmosException naoEncontrado = mock(CosmosException.class);
        CosmosException indisponivel = mock(CosmosException.class);
        when(naoEncontrado.getStatusCode()).thenReturn(404);
        when(indisponivel.getStatusCode()).thenReturn(503);
        when(container.readItem(eq("produto:7"), any(PartitionKey.class), eq(CatalogoProdutoDocument.class)))
                .thenThrow(naoEncontrado)
                .thenThrow(indisponivel);

        assertThat(store.buscarPorCodigoCatalogo(7)).isEmpty();
        assertThatThrownBy(() -> store.buscarPorCodigoCatalogo(7)).isSameAs(indisponivel);
    }

    @ParameterizedTest
    @ValueSource(strings = {"categoria", "categoriaNormalizada", "produto", "produtoNormalizado", "descricao"})
    void deveRejeitarDocumentoComCampoObrigatorioNuloEmPointRead(String campo) {
        CatalogoProdutoDocument document = documento(7, "SÉRUM", "Óleo", 1990L);
        switch (campo) {
            case "categoria" -> document.setCategoria(null);
            case "categoriaNormalizada" -> document.setCategoriaNormalizada(null);
            case "produto" -> document.setProduto(null);
            case "produtoNormalizado" -> document.setProdutoNormalizado(null);
            case "descricao" -> document.setDescricao(null);
            default -> throw new IllegalArgumentException(campo);
        }
        CosmosItemResponse<CatalogoProdutoDocument> response = mock(CosmosItemResponse.class);
        when(response.getItem()).thenReturn(document);
        when(container.readItem("produto:7", new PartitionKey("renovo"), CatalogoProdutoDocument.class))
                .thenReturn(response);

        assertThatThrownBy(() -> store.buscarPorCodigoCatalogo(7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Documento de produto invalido no Cosmos");
    }

    @Test
    void deveRejeitarNormalizacaoIncoerenteVindaDeConsultaPaginada() {
        CatalogoProdutoDocument document = documento(7, "SÉRUM", "Óleo", 1990L);
        document.setCategoriaNormalizada("serum");
        CosmosPagedIterable<CatalogoProdutoDocument> paged = mock(CosmosPagedIterable.class);
        FeedResponse<CatalogoProdutoDocument> page = mock(FeedResponse.class);
        when(page.getResults()).thenReturn(List.of(document));
        when(paged.iterableByPage()).thenReturn(List.of(page));
        when(container.queryItems(any(SqlQuerySpec.class), any(CosmosQueryRequestOptions.class),
                eq(CatalogoProdutoDocument.class))).thenReturn(paged);

        assertThatThrownBy(() -> store.listarTodosOrdenadosPorCodigo())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Documento de produto invalido no Cosmos");
    }

    @Test
    void deveConsumirTodasAsPaginasComConsultaTipadaNormalizadaEParticionada() {
        CosmosPagedIterable<CatalogoProdutoDocument> paged = mock(CosmosPagedIterable.class);
        FeedResponse<CatalogoProdutoDocument> primeiraPagina = mock(FeedResponse.class);
        FeedResponse<CatalogoProdutoDocument> segundaPagina = mock(FeedResponse.class);
        when(primeiraPagina.getResults()).thenReturn(List.of(documento(1, "SÉRUM", "Ácido", 1050L)));
        when(segundaPagina.getResults()).thenReturn(List.of(documento(2, "SÉRUM", "Óleo", 2090L)));
        when(paged.iterableByPage()).thenReturn(List.of(primeiraPagina, segundaPagina));
        when(container.queryItems(any(SqlQuerySpec.class), any(CosmosQueryRequestOptions.class),
                eq(CatalogoProdutoDocument.class))).thenReturn(paged);

        List<ProdutoCatalogo> encontrados = store.listarPorCategoriaOrdenadaPorProduto("SÉRUM");

        assertThat(encontrados).extracting(ProdutoCatalogo::codigoCatalogo).containsExactly(1, 2);
        ArgumentCaptor<SqlQuerySpec> queryCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);
        ArgumentCaptor<CosmosQueryRequestOptions> optionsCaptor =
                ArgumentCaptor.forClass(CosmosQueryRequestOptions.class);
        verify(container).queryItems(queryCaptor.capture(), optionsCaptor.capture(),
                eq(CatalogoProdutoDocument.class));
        assertThat(queryCaptor.getValue().getQueryText())
                .contains("c.catalogoId = @catalogoId")
                .contains("c.tipo = @tipo")
                .contains("c.categoriaNormalizada = @categoria")
                .contains("ORDER BY c.tipo ASC, c.categoriaNormalizada ASC, c.produtoNormalizado ASC");
        assertThat(valorParametro(queryCaptor.getValue(), "@catalogoId", String.class)).isEqualTo("renovo");
        assertThat(valorParametro(queryCaptor.getValue(), "@tipo", String.class)).isEqualTo("produto");
        assertThat(valorParametro(queryCaptor.getValue(), "@categoria", String.class)).isEqualTo("sérum");
        assertThat(optionsCaptor.getValue().getPartitionKey()).isEqualTo(new PartitionKey("renovo"));
    }

    @Test
    void deveUsarCentavosNaConsultaDeFaixaEFiltrarTipoEmTodasAsConsultas() {
        CosmosPagedIterable<CatalogoProdutoDocument> paged = mock(CosmosPagedIterable.class);
        when(paged.iterableByPage()).thenReturn(List.of());
        when(container.queryItems(any(SqlQuerySpec.class), any(CosmosQueryRequestOptions.class),
                eq(CatalogoProdutoDocument.class))).thenReturn(paged);

        store.listarTodosOrdenadosPorCodigo();
        store.pesquisarPorProdutoOrdenadoPorProduto("ÓLEO");
        store.listarPorFaixaDePrecoOrdenadaPorPreco(new BigDecimal("10.10"), new BigDecimal("20.99"));

        ArgumentCaptor<SqlQuerySpec> queryCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);
        verify(container, org.mockito.Mockito.times(3)).queryItems(queryCaptor.capture(),
                any(CosmosQueryRequestOptions.class), eq(CatalogoProdutoDocument.class));
        assertThat(queryCaptor.getAllValues()).allSatisfy(spec ->
                assertThat(spec.getQueryText()).contains("c.catalogoId = @catalogoId", "c.tipo = @tipo"));
        SqlQuerySpec busca = queryCaptor.getAllValues().get(1);
        assertThat(valorParametro(busca, "@produto", String.class)).isEqualTo("óleo");
        SqlQuerySpec faixa = queryCaptor.getAllValues().get(2);
        assertThat(valorParametro(faixa, "@precoMinimo", Long.class)).isEqualTo(1010L);
        assertThat(valorParametro(faixa, "@precoMaximo", Long.class)).isEqualTo(2099L);
    }

    @Test
    void buscaLimitadaUsaTopParametrizadoEChaveDeParticao() {
        CosmosPagedIterable<CatalogoProdutoDocument> paged = mock(CosmosPagedIterable.class);
        when(paged.iterableByPage()).thenReturn(List.of());
        when(container.queryItems(any(SqlQuerySpec.class), any(CosmosQueryRequestOptions.class),
                eq(CatalogoProdutoDocument.class))).thenReturn(paged);

        store.pesquisarPorProdutoLimitado("ÓLEO", 8);

        ArgumentCaptor<SqlQuerySpec> queryCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);
        ArgumentCaptor<CosmosQueryRequestOptions> optionsCaptor =
                ArgumentCaptor.forClass(CosmosQueryRequestOptions.class);
        verify(container).queryItems(queryCaptor.capture(), optionsCaptor.capture(),
                eq(CatalogoProdutoDocument.class));
        assertThat(queryCaptor.getValue().getQueryText())
                .contains("SELECT TOP @limite *", "c.catalogoId = @catalogoId", "CONTAINS");
        assertThat(valorParametro(queryCaptor.getValue(), "@limite", Integer.class)).isEqualTo(8);
        assertThat(valorParametro(queryCaptor.getValue(), "@produto", String.class)).isEqualTo("óleo");
        assertThat(optionsCaptor.getValue().getPartitionKey()).isEqualTo(new PartitionKey("renovo"));
    }

    @Test
    void categoriasLimitadasConsultamSomenteMetadadosComTopParametrizado() {
        CategoriaCatalogoDocument categoria = new CategoriaCatalogoDocument();
        categoria.setCategoria("FACIAL");
        categoria.setCategoriaNormalizada("facial");
        CosmosPagedIterable<CategoriaCatalogoDocument> paged = mock(CosmosPagedIterable.class);
        FeedResponse<CategoriaCatalogoDocument> page = mock(FeedResponse.class);
        when(page.getResults()).thenReturn(List.of(categoria));
        when(paged.iterableByPage()).thenReturn(List.of(page));
        when(container.queryItems(any(SqlQuerySpec.class), any(CosmosQueryRequestOptions.class),
                eq(CategoriaCatalogoDocument.class))).thenReturn(paged);

        assertThat(store.listarCategoriasLimitadas(8)).containsExactly("FACIAL");

        ArgumentCaptor<SqlQuerySpec> queryCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);
        verify(container).queryItems(queryCaptor.capture(), any(CosmosQueryRequestOptions.class),
                eq(CategoriaCatalogoDocument.class));
        assertThat(queryCaptor.getValue().getQueryText())
                .contains("SELECT DISTINCT TOP @limite c.categoria, c.categoriaNormalizada")
                .doesNotContain("SELECT *");
        assertThat(valorParametro(queryCaptor.getValue(), "@limite", Integer.class)).isEqualTo(8);
    }

    @Test
    void deveManterOrderByDasConsultasCompativelComIndicesCompostos() throws Exception {
        CosmosPagedIterable<CatalogoProdutoDocument> paged = mock(CosmosPagedIterable.class);
        when(paged.iterableByPage()).thenReturn(List.of());
        when(container.queryItems(any(SqlQuerySpec.class), any(CosmosQueryRequestOptions.class),
                eq(CatalogoProdutoDocument.class))).thenReturn(paged);

        store.listarTodosOrdenadosPorCodigo();
        store.listarPorCategoriaOrdenadaPorProduto("Categoria");
        store.pesquisarPorProdutoOrdenadoPorProduto("Produto");
        store.listarPorFaixaDePrecoOrdenadaPorPreco(new BigDecimal("10.00"), new BigDecimal("20.00"));

        ArgumentCaptor<SqlQuerySpec> queryCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);
        verify(container, org.mockito.Mockito.times(4)).queryItems(queryCaptor.capture(),
                any(CosmosQueryRequestOptions.class), eq(CatalogoProdutoDocument.class));
        List<List<String>> indices = indicesCompostos();
        assertThat(queryCaptor.getAllValues()).allSatisfy(spec ->
                assertThat(indices).contains(ordemDaConsulta(spec.getQueryText())));
    }

    private ProdutoCatalogo produto(
            String id,
            String legacyId,
            int codigo,
            String categoria,
            String nome,
            String preco
    ) {
        return new ProdutoCatalogo(
                id,
                legacyId,
                codigo,
                categoria,
                nome,
                "Descricao",
                new BigDecimal(preco),
                null,
                "https://catalogo.exemplo/produto"
        );
    }

    private CatalogoProdutoDocument documento(int codigo, String categoria, String nome, long centavos) {
        return new CatalogoProdutoDocument(
                "produto:" + codigo,
                "renovo",
                "produto",
                1,
                null,
                codigo,
                categoria,
                categoria.toLowerCase(),
                nome,
                nome.toLowerCase(),
                "Descricao",
                centavos,
                null,
                null
        );
    }

    private <T> T valorParametro(SqlQuerySpec spec, String nome, Class<T> tipo) {
        SqlParameter parameter = spec.getParameters().stream()
                .filter(item -> item.getName().equals(nome))
                .findFirst()
                .orElseThrow();
        return parameter.getValue(tipo);
    }

    private List<List<String>> indicesCompostos() throws Exception {
        JsonNode root = new ObjectMapper().readTree(
                Path.of("infra", "cosmos", "catalogo-indexing-policy.json").toFile()
        );
        List<List<String>> indices = new ArrayList<>();
        root.path("compositeIndexes").forEach(index -> {
            List<String> campos = new ArrayList<>();
            index.forEach(item -> campos.add(
                    item.path("path").asText().substring(1) + ":" + item.path("order").asText()
            ));
            indices.add(List.copyOf(campos));
        });
        return List.copyOf(indices);
    }

    private List<String> ordemDaConsulta(String query) {
        String orderBy = query.substring(query.indexOf("ORDER BY ") + "ORDER BY ".length());
        return Arrays.stream(orderBy.split(","))
                .map(String::trim)
                .map(item -> item.split("\\s+"))
                .map(tokens -> tokens[0].substring("c.".length()) + ":" + direcaoIndice(tokens[1]))
                .toList();
    }

    private String direcaoIndice(String direction) {
        return switch (direction.toUpperCase()) {
            case "ASC" -> "ascending";
            case "DESC" -> "descending";
            default -> throw new IllegalArgumentException(direction);
        };
    }
}
