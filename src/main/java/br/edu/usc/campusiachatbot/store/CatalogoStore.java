package br.edu.usc.campusiachatbot.store;

import br.edu.usc.campusiachatbot.domain.ProdutoCatalogo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CatalogoStore {

    int LIMITE_MAXIMO_CONSULTA_IA = 20;

    ProdutoCatalogo salvar(ProdutoCatalogo produto);

    void salvarTodos(List<ProdutoCatalogo> produtos);

    List<ProdutoCatalogo> listarTodosOrdenadosPorCodigo();

    Optional<ProdutoCatalogo> buscarPorId(String id);

    Optional<ProdutoCatalogo> buscarPorCodigoCatalogo(Integer codigoCatalogo);

    List<ProdutoCatalogo> listarPorCategoriaOrdenadaPorProduto(String categoria);

    List<ProdutoCatalogo> pesquisarPorProdutoOrdenadoPorProduto(String produto);

    List<ProdutoCatalogo> listarPorFaixaDePrecoOrdenadaPorPreco(BigDecimal precoMinimo, BigDecimal precoMaximo);

    List<String> listarCategoriasLimitadas(int limite);

    List<ProdutoCatalogo> listarPorCategoriaLimitada(String categoria, int limite);

    List<ProdutoCatalogo> pesquisarPorProdutoLimitado(String produto, int limite);

    List<ProdutoCatalogo> listarPorFaixaDePrecoLimitada(
            BigDecimal precoMinimo,
            BigDecimal precoMaximo,
            int limite
    );

    void excluir(String id);
}
