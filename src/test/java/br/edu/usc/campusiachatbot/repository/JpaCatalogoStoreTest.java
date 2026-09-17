package br.edu.usc.campusiachatbot.repository;

import br.edu.usc.campusiachatbot.entity.CatalogoRenovoEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaCatalogoStoreTest {

    private CatalogoRenovoRepository repository;
    private JpaCatalogoStore store;

    @BeforeEach
    void configurar() {
        repository = mock(CatalogoRenovoRepository.class);
        store = new JpaCatalogoStore(repository);
    }

    @Test
    void buscaPorCategoriaAplicaLimiteNaConsultaJpa() {
        when(repository.findByCategoriaIgnoreCaseOrderByProdutoAsc(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of(produto()));

        assertThat(store.listarPorCategoriaLimitada(" Facial ", 8)).hasSize(1);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByCategoriaIgnoreCaseOrderByProdutoAsc(
                org.mockito.ArgumentMatchers.eq("Facial"),
                pageable.capture()
        );
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(8);
    }

    @Test
    void categoriasAplicamLimiteNaConsultaJpa() {
        when(repository.listarCategorias(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of("CAPILAR", "FACIAL"));

        assertThat(store.listarCategoriasLimitadas(8)).containsExactly("CAPILAR", "FACIAL");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).listarCategorias(pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(8);
    }

    private CatalogoRenovoEntity produto() {
        CatalogoRenovoEntity produto = new CatalogoRenovoEntity();
        produto.setId(1L);
        produto.setCodigoCatalogo(1);
        produto.setCategoria("FACIAL");
        produto.setProduto("Serum");
        produto.setDescricao("Descricao");
        produto.setPrecoAtual(new BigDecimal("79.90"));
        return produto;
    }
}
