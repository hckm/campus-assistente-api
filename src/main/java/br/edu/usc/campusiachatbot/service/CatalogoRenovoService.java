package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.domain.ProdutoCatalogo;
import br.edu.usc.campusiachatbot.store.CatalogoStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatalogoRenovoService {

    private final CatalogoStore catalogoStore;

    public ProdutoCatalogo salvar(ProdutoCatalogo produtoCatalogo) {
        return catalogoStore.salvar(produtoCatalogo);
    }

    public List<ProdutoCatalogo> listarTodos() {
        return catalogoStore.listarTodosOrdenadosPorCodigo();
    }

    public Optional<ProdutoCatalogo> buscarPorId(String id) {
        return catalogoStore.buscarPorId(id);
    }

    public Optional<ProdutoCatalogo> buscarPorCodigoCatalogo(Integer codigoCatalogo) {
        return catalogoStore.buscarPorCodigoCatalogo(codigoCatalogo);
    }

    public List<ProdutoCatalogo> listarPorCategoria(String categoria) {
        return catalogoStore.listarPorCategoriaOrdenadaPorProduto(categoria);
    }

    public List<ProdutoCatalogo> pesquisarPorProduto(String produto) {
        return catalogoStore.pesquisarPorProdutoOrdenadoPorProduto(produto);
    }

    public List<ProdutoCatalogo> listarPorFaixaDePreco(BigDecimal precoMinimo, BigDecimal precoMaximo) {
        return catalogoStore.listarPorFaixaDePrecoOrdenadaPorPreco(precoMinimo, precoMaximo);
    }

    public void excluir(String id) {
        catalogoStore.excluir(id);
    }
}
