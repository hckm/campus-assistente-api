package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.store.CatalogoStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "gemini.api-key=")
@ActiveProfiles("test")
class CatalogoRenovoDataLoaderTransactionalTest {

    @Autowired
    private CatalogoRenovoDataLoader loader;

    @MockitoBean
    private CatalogoStore catalogoStore;

    @Test
    void deveExecutarConsultasEGravacaoNaMesmaTransacao() throws Exception {
        reset(catalogoStore);
        String[] transacaoConsulta = new String[1];
        String[] transacaoGravacao = new String[1];

        when(catalogoStore.buscarPorCodigoCatalogo(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            transacaoConsulta[0] = TransactionSynchronizationManager.getCurrentTransactionName();
            return Optional.empty();
        });
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            transacaoGravacao[0] = TransactionSynchronizationManager.getCurrentTransactionName();
            return null;
        }).when(catalogoStore).salvarTodos(any());

        loader.run(null);

        assertThat(transacaoConsulta[0]).isNotBlank();
        assertThat(transacaoGravacao[0]).isEqualTo(transacaoConsulta[0]);
    }
}
