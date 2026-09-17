package br.edu.usc.campusiachatbot.repository;

import br.edu.usc.campusiachatbot.domain.ProdutoCatalogo;
import br.edu.usc.campusiachatbot.entity.CatalogoRenovoEntity;
import br.edu.usc.campusiachatbot.store.CatalogoStore;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "catalogo", name = "backend", havingValue = "JPA", matchIfMissing = true)
public class JpaCatalogoStore implements CatalogoStore {

    private final CatalogoRenovoRepository catalogoRenovoRepository;

    @Override
    @Transactional
    public ProdutoCatalogo salvar(ProdutoCatalogo produto) {
        return toDomain(catalogoRenovoRepository.save(toEntity(produto)));
    }

    @Override
    @Transactional
    public void salvarTodos(List<ProdutoCatalogo> produtos) {
        catalogoRenovoRepository.saveAll(produtos.stream().map(this::toEntity).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProdutoCatalogo> listarTodosOrdenadosPorCodigo() {
        return catalogoRenovoRepository.findAll(Sort.by(Sort.Direction.ASC, "codigoCatalogo"))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProdutoCatalogo> buscarPorId(String id) {
        return catalogoRenovoRepository.findById(toJpaId(id)).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProdutoCatalogo> buscarPorCodigoCatalogo(Integer codigoCatalogo) {
        return catalogoRenovoRepository.findByCodigoCatalogo(codigoCatalogo).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProdutoCatalogo> listarPorCategoriaOrdenadaPorProduto(String categoria) {
        return catalogoRenovoRepository.findByCategoriaIgnoreCaseOrderByProdutoAsc(categoria)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProdutoCatalogo> pesquisarPorProdutoOrdenadoPorProduto(String produto) {
        return catalogoRenovoRepository.findByProdutoContainingIgnoreCaseOrderByProdutoAsc(produto)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProdutoCatalogo> listarPorFaixaDePrecoOrdenadaPorPreco(
            BigDecimal precoMinimo,
            BigDecimal precoMaximo
    ) {
        return catalogoRenovoRepository.findByPrecoAtualBetweenOrderByPrecoAtualAsc(precoMinimo, precoMaximo)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listarCategoriasLimitadas(int limite) {
        return List.copyOf(catalogoRenovoRepository.listarCategorias(PageRequest.of(0, validarLimite(limite))));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProdutoCatalogo> listarPorCategoriaLimitada(String categoria, int limite) {
        validarTermo(categoria, "categoria");
        return catalogoRenovoRepository.findByCategoriaIgnoreCaseOrderByProdutoAsc(
                        categoria.trim(),
                        PageRequest.of(0, validarLimite(limite))
                )
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProdutoCatalogo> pesquisarPorProdutoLimitado(String produto, int limite) {
        validarTermo(produto, "produto");
        return catalogoRenovoRepository.findByProdutoContainingIgnoreCaseOrderByProdutoAsc(
                        produto.trim(),
                        PageRequest.of(0, validarLimite(limite))
                )
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProdutoCatalogo> listarPorFaixaDePrecoLimitada(
            BigDecimal precoMinimo,
            BigDecimal precoMaximo,
            int limite
    ) {
        validarFaixa(precoMinimo, precoMaximo);
        return catalogoRenovoRepository.findByPrecoAtualBetweenOrderByPrecoAtualAsc(
                        precoMinimo,
                        precoMaximo,
                        PageRequest.of(0, validarLimite(limite))
                )
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void excluir(String id) {
        catalogoRenovoRepository.deleteById(toJpaId(id));
    }

    private CatalogoRenovoEntity toEntity(ProdutoCatalogo produto) {
        CatalogoRenovoEntity entity = new CatalogoRenovoEntity();
        entity.setId(produto.id() == null ? null : toJpaId(produto.id()));
        entity.setCodigoCatalogo(produto.codigoCatalogo());
        entity.setCategoria(produto.categoria());
        entity.setProduto(produto.produto());
        entity.setDescricao(produto.descricao());
        entity.setPrecoAtual(produto.precoAtual());
        entity.setPrecoOriginal(produto.precoOriginal());
        entity.setUrlCatalogo(produto.urlCatalogo());
        return entity;
    }

    private ProdutoCatalogo toDomain(CatalogoRenovoEntity entity) {
        return new ProdutoCatalogo(
                entity.getId().toString(),
                null,
                entity.getCodigoCatalogo(),
                entity.getCategoria(),
                entity.getProduto(),
                entity.getDescricao(),
                entity.getPrecoAtual(),
                entity.getPrecoOriginal(),
                entity.getUrlCatalogo()
        );
    }

    private Long toJpaId(String id) {
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("ID interno de catalogo invalido para backend JPA", exception);
        }
    }

    private int validarLimite(int limite) {
        if (limite < 1 || limite > LIMITE_MAXIMO_CONSULTA_IA) {
            throw new IllegalArgumentException("Limite de consulta do catalogo invalido");
        }
        return limite;
    }

    private void validarTermo(String termo, String campo) {
        if (termo == null || termo.isBlank()) {
            throw new IllegalArgumentException(campo + " e obrigatorio");
        }
    }

    private void validarFaixa(BigDecimal precoMinimo, BigDecimal precoMaximo) {
        if (precoMinimo == null || precoMaximo == null
                || precoMinimo.signum() < 0
                || precoMaximo.signum() < 0
                || precoMinimo.compareTo(precoMaximo) > 0) {
            throw new IllegalArgumentException("Faixa de preco invalida");
        }
    }
}
