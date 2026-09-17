package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.domain.ProdutoCatalogo;
import br.edu.usc.campusiachatbot.repository.CatalogoRenovoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "gemini.api-key=")
@ActiveProfiles("test")
class CatalogoRenovoServiceTest {

    @Autowired
    private CatalogoRenovoService catalogoRenovoService;

    @Autowired
    private CatalogoRenovoRepository catalogoRenovoRepository;

    @BeforeEach
    void setUp() {
        catalogoRenovoRepository.deleteAll();
    }

    @Test
    void deveSalvarEListarProdutos() {
        catalogoRenovoService.salvar(produto(1, "HIDRATANTE", "Creme A", "10.00"));
        catalogoRenovoService.salvar(produto(2, "SORO", "Soro B", "25.00"));

        List<ProdutoCatalogo> lista = catalogoRenovoService.listarTodos();

        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).codigoCatalogo()).isEqualTo(1);
        assertThat(lista.get(1).codigoCatalogo()).isEqualTo(2);
    }

    @Test
    void deveBuscarPorId() {
        ProdutoCatalogo salvo = catalogoRenovoService.salvar(produto(10, "CAT", "Produto X", "50.00"));

        Optional<ProdutoCatalogo> encontrado = catalogoRenovoService.buscarPorId(salvo.id());

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().produto()).isEqualTo("Produto X");
    }

    @Test
    void deveBuscarPorCodigoCatalogo() {
        catalogoRenovoService.salvar(produto(42, "CAT", "Produto 42", "30.00"));

        Optional<ProdutoCatalogo> encontrado = catalogoRenovoService.buscarPorCodigoCatalogo(42);

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().codigoCatalogo()).isEqualTo(42);
    }

    @Test
    void deveListarPorCategoria() {
        catalogoRenovoService.salvar(produto(1, "HIDRATANTE", "Creme A", "10.00"));
        catalogoRenovoService.salvar(produto(2, "SORO", "Soro B", "25.00"));
        catalogoRenovoService.salvar(produto(3, "HIDRATANTE", "Creme C", "15.00"));

        List<ProdutoCatalogo> hidratantes = catalogoRenovoService.listarPorCategoria("hidratante");

        assertThat(hidratantes).hasSize(2);
        assertThat(hidratantes).extracting(ProdutoCatalogo::produto).containsExactly("Creme A", "Creme C");
        assertThat(hidratantes).allMatch(p -> p.categoria().equals("HIDRATANTE"));
    }

    @Test
    void devePesquisarPorProduto() {
        catalogoRenovoService.salvar(produto(1, "SORO", "Serum Facial", "80.00"));
        catalogoRenovoService.salvar(produto(2, "CREME", "Creme Hidratante", "40.00"));

        List<ProdutoCatalogo> resultado = catalogoRenovoService.pesquisarPorProduto("serum");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).produto()).isEqualTo("Serum Facial");
    }

    @Test
    void deveListarPorFaixaDePreco() {
        catalogoRenovoService.salvar(produto(1, "CAT", "Barato", "10.00"));
        catalogoRenovoService.salvar(produto(2, "CAT", "Medio", "50.00"));
        catalogoRenovoService.salvar(produto(3, "CAT", "Caro", "200.00"));

        List<ProdutoCatalogo> resultado = catalogoRenovoService.listarPorFaixaDePreco(
                new BigDecimal("20.00"), new BigDecimal("100.00")
        );

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).produto()).isEqualTo("Medio");
    }

    @Test
    void deveExcluirProduto() {
        ProdutoCatalogo salvo = catalogoRenovoService.salvar(produto(99, "CAT", "Temporario", "5.00"));

        catalogoRenovoService.excluir(salvo.id());

        assertThat(catalogoRenovoService.buscarPorId(salvo.id())).isEmpty();
    }

    @Test
    void deveAtualizarProdutoSemDuplicarCodigoEPreservarValoresNulos() {
        ProdutoCatalogo salvo = catalogoRenovoService.salvar(produto(7, "CAT", "Produto antigo", "10.10"));
        ProdutoCatalogo atualizado = new ProdutoCatalogo(
                salvo.id(),
                salvo.legacyId(),
                salvo.codigoCatalogo(),
                "NOVA",
                "Produto novo",
                "Descricao atualizada",
                new BigDecimal("12.34"),
                null,
                null
        );

        ProdutoCatalogo resultado = catalogoRenovoService.salvar(atualizado);

        assertThat(catalogoRenovoRepository.count()).isEqualTo(1);
        assertThat(resultado.id()).isEqualTo(salvo.id());
        assertThat(resultado.codigoCatalogo()).isEqualTo(7);
        assertThat(resultado.precoAtual()).isEqualByComparingTo("12.34");
        assertThat(resultado.precoOriginal()).isNull();
        assertThat(resultado.urlCatalogo()).isNull();
    }

    private ProdutoCatalogo produto(int codigo, String categoria, String nome, String preco) {
        return new ProdutoCatalogo(
                null,
                null,
                codigo,
                categoria,
                nome,
                "Descricao de " + nome,
                new BigDecimal(preco),
                null,
                null
        );
    }
}
