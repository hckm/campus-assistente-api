package br.edu.usc.campusiachatbot.repository;

import br.edu.usc.campusiachatbot.config.CosmosProperties;
import br.edu.usc.campusiachatbot.domain.ProdutoCatalogo;
import br.edu.usc.campusiachatbot.store.CatalogoStore;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosBatch;
import com.azure.cosmos.models.CosmosBatchOperationResult;
import com.azure.cosmos.models.CosmosBatchResponse;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class CosmosCatalogoStore implements CatalogoStore {

    private static final String TIPO_PRODUTO = "produto";
    private static final int SCHEMA_VERSION = 1;
    private static final int BATCH_ENVELOPE_BYTES = 1024;
    private static final int BATCH_OPERATION_MARGIN_BYTES = 256;

    private final CosmosContainer container;
    private final CosmosProperties properties;
    private final ObjectMapper objectMapper;
    private final PartitionKey partitionKey;

    public CosmosCatalogoStore(CosmosClient client, CosmosProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.partitionKey = new PartitionKey(properties.getCatalogoId());
        this.container = client.getDatabase(properties.getDatabase())
                .getContainer(properties.getCatalogoContainer());
    }

    @Override
    public ProdutoCatalogo salvar(ProdutoCatalogo produto) {
        CatalogoProdutoDocument document = toDocument(produto);
        validarTamanho(List.of(document));
        CosmosItemResponse<CatalogoProdutoDocument> response = container.upsertItem(
                document,
                partitionKey,
                new CosmosItemRequestOptions()
        );
        if (!statusSucesso(response.getStatusCode())) {
            throw new IllegalStateException(
                    "Falha ao salvar produto no Cosmos; status=" + response.getStatusCode()
            );
        }
        return toDomain(document);
    }

    @Override
    public void salvarTodos(List<ProdutoCatalogo> produtos) {
        if (produtos.isEmpty()) {
            return;
        }
        if (produtos.size() > properties.getCatalogoBatchMaxOperations()) {
            throw new IllegalArgumentException("Lote do catalogo excede o limite de operacoes configurado");
        }

        List<CatalogoProdutoDocument> documents = produtos.stream().map(this::toDocument).toList();
        validarIdsDuplicados(documents);
        validarTamanho(documents);

        CosmosBatch batch = CosmosBatch.createCosmosBatch(partitionKey);
        documents.forEach(batch::upsertItemOperation);
        CosmosBatchResponse response = container.executeCosmosBatch(batch);
        validarRespostaBatch(response, documents.size());
    }

    public boolean criarTodosSeVazio(List<ProdutoCatalogo> produtos) {
        if (produtos.isEmpty()) {
            throw new IllegalArgumentException("Carga inicial do catalogo nao pode estar vazia");
        }
        if (!listarTodosOrdenadosPorCodigo().isEmpty()) {
            return false;
        }
        if (produtos.size() > properties.getCatalogoBatchMaxOperations()) {
            throw new IllegalArgumentException("Lote do catalogo excede o limite de operacoes configurado");
        }

        List<CatalogoProdutoDocument> documents = produtos.stream().map(this::toDocument).toList();
        validarIdsDuplicados(documents);
        validarTamanho(documents);

        CosmosBatch batch = CosmosBatch.createCosmosBatch(partitionKey);
        documents.forEach(batch::createItemOperation);
        CosmosBatchResponse response = container.executeCosmosBatch(batch);
        if (contemConflito(response)) {
            if (!listarTodosOrdenadosPorCodigo().isEmpty()) {
                return false;
            }
        }
        validarRespostaBatch(response, documents.size());
        return true;
    }

    @Override
    public List<ProdutoCatalogo> listarTodosOrdenadosPorCodigo() {
        return consultar(
                "SELECT * FROM c WHERE c.catalogoId = @catalogoId AND c.tipo = @tipo "
                        + "ORDER BY c.tipo ASC, c.codigoCatalogo ASC",
                List.of()
        );
    }

    @Override
    public Optional<ProdutoCatalogo> buscarPorId(String id) {
        validarIdFisico(id);
        return readItem(id);
    }

    @Override
    public Optional<ProdutoCatalogo> buscarPorCodigoCatalogo(Integer codigoCatalogo) {
        validarCodigo(codigoCatalogo);
        return readItem(idFisico(codigoCatalogo));
    }

    @Override
    public List<ProdutoCatalogo> listarPorCategoriaOrdenadaPorProduto(String categoria) {
        return consultar(
                "SELECT * FROM c WHERE c.catalogoId = @catalogoId AND c.tipo = @tipo "
                        + "AND c.categoriaNormalizada = @categoria "
                        + "ORDER BY c.tipo ASC, c.categoriaNormalizada ASC, c.produtoNormalizado ASC",
                List.of(new SqlParameter("@categoria", normalizar(categoria, "categoria")))
        );
    }

    @Override
    public List<ProdutoCatalogo> pesquisarPorProdutoOrdenadoPorProduto(String produto) {
        return consultar(
                "SELECT * FROM c WHERE c.catalogoId = @catalogoId AND c.tipo = @tipo "
                        + "AND CONTAINS(c.produtoNormalizado, @produto) "
                        + "ORDER BY c.tipo ASC, c.produtoNormalizado ASC",
                List.of(new SqlParameter("@produto", normalizar(produto, "produto")))
        );
    }

    @Override
    public List<ProdutoCatalogo> listarPorFaixaDePrecoOrdenadaPorPreco(
            BigDecimal precoMinimo,
            BigDecimal precoMaximo
    ) {
        return consultar(
                "SELECT * FROM c WHERE c.catalogoId = @catalogoId AND c.tipo = @tipo "
                        + "AND c.precoAtualCentavos >= @precoMinimo "
                        + "AND c.precoAtualCentavos <= @precoMaximo "
                        + "ORDER BY c.tipo ASC, c.precoAtualCentavos ASC",
                List.of(
                        new SqlParameter("@precoMinimo", toCentavos(precoMinimo, "precoMinimo")),
                        new SqlParameter("@precoMaximo", toCentavos(precoMaximo, "precoMaximo"))
                )
        );
    }

    @Override
    public List<String> listarCategoriasLimitadas(int limite) {
        int limiteValidado = validarLimite(limite);
        List<SqlParameter> parameters = parametrosBase();
        parameters.add(new SqlParameter("@limite", limiteValidado));
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT DISTINCT TOP @limite c.categoria, c.categoriaNormalizada FROM c "
                        + "WHERE c.catalogoId = @catalogoId AND c.tipo = @tipo "
                        + "ORDER BY c.categoriaNormalizada ASC",
                parameters
        );
        CosmosQueryRequestOptions options = opcoesConsulta(limiteValidado);
        List<String> categorias = new ArrayList<>();
        container.queryItems(querySpec, options, CategoriaCatalogoDocument.class)
                .iterableByPage()
                .forEach(page -> page.getResults().forEach(document -> {
                    validarCategoriaDocument(document);
                    categorias.add(document.getCategoria());
                }));
        return List.copyOf(categorias);
    }

    @Override
    public List<ProdutoCatalogo> listarPorCategoriaLimitada(String categoria, int limite) {
        return consultarLimitado(
                "SELECT TOP @limite * FROM c WHERE c.catalogoId = @catalogoId AND c.tipo = @tipo "
                        + "AND c.categoriaNormalizada = @categoria "
                        + "ORDER BY c.tipo ASC, c.categoriaNormalizada ASC, c.produtoNormalizado ASC",
                List.of(new SqlParameter("@categoria", normalizar(categoria, "categoria"))),
                limite
        );
    }

    @Override
    public List<ProdutoCatalogo> pesquisarPorProdutoLimitado(String produto, int limite) {
        return consultarLimitado(
                "SELECT TOP @limite * FROM c WHERE c.catalogoId = @catalogoId AND c.tipo = @tipo "
                        + "AND CONTAINS(c.produtoNormalizado, @produto) "
                        + "ORDER BY c.tipo ASC, c.produtoNormalizado ASC",
                List.of(new SqlParameter("@produto", normalizar(produto, "produto"))),
                limite
        );
    }

    @Override
    public List<ProdutoCatalogo> listarPorFaixaDePrecoLimitada(
            BigDecimal precoMinimo,
            BigDecimal precoMaximo,
            int limite
    ) {
        validarFaixa(precoMinimo, precoMaximo);
        return consultarLimitado(
                "SELECT TOP @limite * FROM c WHERE c.catalogoId = @catalogoId AND c.tipo = @tipo "
                        + "AND c.precoAtualCentavos >= @precoMinimo "
                        + "AND c.precoAtualCentavos <= @precoMaximo "
                        + "ORDER BY c.tipo ASC, c.precoAtualCentavos ASC",
                List.of(
                        new SqlParameter("@precoMinimo", toCentavos(precoMinimo, "precoMinimo")),
                        new SqlParameter("@precoMaximo", toCentavos(precoMaximo, "precoMaximo"))
                ),
                limite
        );
    }

    @Override
    public void excluir(String id) {
        validarIdFisico(id);
        container.deleteItem(id, partitionKey, new CosmosItemRequestOptions());
    }

    private Optional<ProdutoCatalogo> readItem(String id) {
        try {
            CatalogoProdutoDocument document = container.readItem(
                    id,
                    partitionKey,
                    CatalogoProdutoDocument.class
            ).getItem();
            return Optional.of(toDomain(document));
        } catch (CosmosException exception) {
            if (exception.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private List<ProdutoCatalogo> consultar(String query, List<SqlParameter> parameters) {
        List<SqlParameter> allParameters = parametrosBase();
        allParameters.addAll(parameters);
        SqlQuerySpec querySpec = new SqlQuerySpec(query, allParameters);
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions().setPartitionKey(partitionKey);
        List<ProdutoCatalogo> produtos = new ArrayList<>();
        container.queryItems(querySpec, options, CatalogoProdutoDocument.class)
                .iterableByPage()
                .forEach(page -> page.getResults().stream().map(this::toDomain).forEach(produtos::add));
        return List.copyOf(produtos);
    }

    private List<ProdutoCatalogo> consultarLimitado(
            String query,
            List<SqlParameter> parameters,
            int limite
    ) {
        int limiteValidado = validarLimite(limite);
        List<SqlParameter> allParameters = parametrosBase();
        allParameters.add(new SqlParameter("@limite", limiteValidado));
        allParameters.addAll(parameters);
        SqlQuerySpec querySpec = new SqlQuerySpec(query, allParameters);
        CosmosQueryRequestOptions options = opcoesConsulta(limiteValidado);
        List<ProdutoCatalogo> produtos = new ArrayList<>();
        container.queryItems(querySpec, options, CatalogoProdutoDocument.class)
                .iterableByPage()
                .forEach(page -> page.getResults().stream().map(this::toDomain).forEach(produtos::add));
        return List.copyOf(produtos);
    }

    private List<SqlParameter> parametrosBase() {
        List<SqlParameter> parameters = new ArrayList<>();
        parameters.add(new SqlParameter("@catalogoId", properties.getCatalogoId()));
        parameters.add(new SqlParameter("@tipo", TIPO_PRODUTO));
        return parameters;
    }

    private CosmosQueryRequestOptions opcoesConsulta(int limite) {
        validarLimite(limite);
        return new CosmosQueryRequestOptions().setPartitionKey(partitionKey);
    }

    private CatalogoProdutoDocument toDocument(ProdutoCatalogo produto) {
        if (produto == null) {
            throw new IllegalArgumentException("Produto do catalogo nao pode ser nulo");
        }
        validarCodigo(produto.codigoCatalogo());
        validarTexto(produto.categoria(), "categoria", 60);
        validarTexto(produto.produto(), "produto", 160);
        validarTexto(produto.descricao(), "descricao", 1000);
        validarTextoOpcional(produto.urlCatalogo(), "urlCatalogo", 255);
        String id = idFisico(produto.codigoCatalogo());
        String legacyId = resolverLegacyId(produto, id);
        return new CatalogoProdutoDocument(
                id,
                properties.getCatalogoId(),
                TIPO_PRODUTO,
                SCHEMA_VERSION,
                legacyId,
                produto.codigoCatalogo(),
                produto.categoria(),
                normalizar(produto.categoria(), "categoria"),
                produto.produto(),
                normalizar(produto.produto(), "produto"),
                produto.descricao(),
                toCentavos(produto.precoAtual(), "precoAtual"),
                produto.precoOriginal() == null ? null : toCentavos(produto.precoOriginal(), "precoOriginal"),
                produto.urlCatalogo()
        );
    }

    private ProdutoCatalogo toDomain(CatalogoProdutoDocument document) {
        validarDocument(document);
        return new ProdutoCatalogo(
                document.getId(),
                document.getLegacyId(),
                document.getCodigoCatalogo(),
                document.getCategoria(),
                document.getProduto(),
                document.getDescricao(),
                BigDecimal.valueOf(document.getPrecoAtualCentavos(), 2),
                document.getPrecoOriginalCentavos() == null
                        ? null
                        : BigDecimal.valueOf(document.getPrecoOriginalCentavos(), 2),
                document.getUrlCatalogo()
        );
    }

    private void validarDocument(CatalogoProdutoDocument document) {
        if (document == null
                || !TIPO_PRODUTO.equals(document.getTipo())
                || document.getSchemaVersion() != SCHEMA_VERSION
                || !properties.getCatalogoId().equals(document.getCatalogoId())
                || document.getCodigoCatalogo() == null
                || !idFisico(document.getCodigoCatalogo()).equals(document.getId())
                || document.getCategoria() == null
                || document.getCategoriaNormalizada() == null
                || !document.getCategoria().toLowerCase(Locale.ROOT).equals(document.getCategoriaNormalizada())
                || document.getProduto() == null
                || document.getProdutoNormalizado() == null
                || !document.getProduto().toLowerCase(Locale.ROOT).equals(document.getProdutoNormalizado())
                || document.getDescricao() == null
                || document.getPrecoAtualCentavos() == null) {
            throw new IllegalStateException("Documento de produto invalido no Cosmos");
        }
    }

    private String resolverLegacyId(ProdutoCatalogo produto, String idEsperado) {
        String idRecebido = produto.id();
        String legacyId = produto.legacyId();
        if (idRecebido != null) {
            if (idRecebido.startsWith(TIPO_PRODUTO + ":")) {
                if (!idRecebido.equals(idEsperado)) {
                    throw new IllegalArgumentException("ID Cosmos diverge do codigoCatalogo");
                }
            } else {
                validarLegacyId(idRecebido);
                if (legacyId != null && !legacyId.equals(idRecebido)) {
                    throw new IllegalArgumentException("legacyId diverge do ID decimal recebido");
                }
                legacyId = idRecebido;
            }
        }
        if (legacyId != null) {
            validarLegacyId(legacyId);
        }
        return legacyId;
    }

    private void validarLegacyId(String id) {
        try {
            Long.valueOf(id);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("ID interno de catalogo deve ser decimal ou deterministico", exception);
        }
    }

    private void validarIdFisico(String id) {
        if (id == null || !id.startsWith(TIPO_PRODUTO + ":")) {
            throw new IllegalArgumentException("ID Cosmos de produto invalido");
        }
        try {
            Integer codigo = Integer.valueOf(id.substring((TIPO_PRODUTO + ":").length()));
            if (!idFisico(codigo).equals(id)) {
                throw new IllegalArgumentException("ID Cosmos de produto invalido");
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("ID Cosmos de produto invalido", exception);
        }
    }

    private void validarCodigo(Integer codigoCatalogo) {
        if (codigoCatalogo == null) {
            throw new IllegalArgumentException("codigoCatalogo e obrigatorio");
        }
    }

    private void validarTexto(String value, String field, int maxLength) {
        if (value == null || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " invalido para o catalogo");
        }
    }

    private void validarTextoOpcional(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(field + " invalido para o catalogo");
        }
    }

    private String normalizar(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " e obrigatorio");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private int validarLimite(int limite) {
        if (limite < 1 || limite > LIMITE_MAXIMO_CONSULTA_IA) {
            throw new IllegalArgumentException("Limite de consulta do catalogo invalido");
        }
        return limite;
    }

    private void validarFaixa(BigDecimal precoMinimo, BigDecimal precoMaximo) {
        if (precoMinimo == null || precoMaximo == null
                || precoMinimo.signum() < 0
                || precoMaximo.signum() < 0
                || precoMinimo.compareTo(precoMaximo) > 0) {
            throw new IllegalArgumentException("Faixa de preco invalida");
        }
    }

    private void validarCategoriaDocument(CategoriaCatalogoDocument document) {
        if (document == null
                || document.getCategoria() == null
                || document.getCategoriaNormalizada() == null
                || !document.getCategoria().toLowerCase(Locale.ROOT)
                .equals(document.getCategoriaNormalizada())) {
            throw new IllegalStateException("Documento de categoria invalido no Cosmos");
        }
    }

    private long toCentavos(BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " e obrigatorio");
        }
        try {
            return value.movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(field + " nao pode ser representado exatamente em centavos", exception);
        }
    }

    private String idFisico(Integer codigoCatalogo) {
        return TIPO_PRODUTO + ":" + codigoCatalogo;
    }

    private void validarIdsDuplicados(List<CatalogoProdutoDocument> documents) {
        Set<String> ids = new HashSet<>();
        for (CatalogoProdutoDocument document : documents) {
            if (!ids.add(document.getId())) {
                throw new IllegalArgumentException("Lote do catalogo contem codigoCatalogo duplicado");
            }
        }
    }

    private void validarTamanho(List<CatalogoProdutoDocument> documents) {
        long bytes = BATCH_ENVELOPE_BYTES;
        for (CatalogoProdutoDocument document : documents) {
            try {
                bytes = Math.addExact(bytes, objectMapper.writeValueAsBytes(document).length);
                bytes = Math.addExact(bytes, BATCH_OPERATION_MARGIN_BYTES);
            } catch (JsonProcessingException | ArithmeticException exception) {
                throw new IllegalArgumentException("Nao foi possivel validar o tamanho do lote do catalogo", exception);
            }
        }
        if (bytes > properties.getCatalogoBatchMaxBytes()) {
            throw new IllegalArgumentException("Lote do catalogo excede o limite de bytes UTF-8 configurado");
        }
    }

    private void validarRespostaBatch(CosmosBatchResponse response, int expectedResults) {
        if (response == null || !response.isSuccessStatusCode()) {
            int status = response == null ? 0 : response.getStatusCode();
            throw new IllegalStateException("Falha no batch Cosmos do catalogo; status=" + status);
        }
        List<CosmosBatchOperationResult> results = response.getResults();
        if (results == null || results.size() != expectedResults) {
            throw new IllegalStateException("Batch Cosmos retornou quantidade de resultados inesperada");
        }
        for (int index = 0; index < results.size(); index++) {
            CosmosBatchOperationResult result = results.get(index);
            if (result == null || !result.isSuccessStatusCode()) {
                int status = result == null ? 0 : result.getStatusCode();
                throw new IllegalStateException(
                        "Falha em operacao do batch Cosmos do catalogo; indice=" + index + "; status=" + status
                );
            }
        }
    }

    private boolean contemConflito(CosmosBatchResponse response) {
        return response != null && response.getResults() != null
                && response.getResults().stream().anyMatch(result -> result != null && result.getStatusCode() == 409);
    }

    private boolean statusSucesso(int status) {
        return status >= 200 && status < 300;
    }
}
