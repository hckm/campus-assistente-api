package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.client.AzureOpenAiChatResponse;
import br.edu.usc.campusiachatbot.client.AzureOpenAiClient;
import br.edu.usc.campusiachatbot.client.AzureOpenAiClientException;
import br.edu.usc.campusiachatbot.client.AzureOpenAiToolCall;
import br.edu.usc.campusiachatbot.config.CatalogoConsultaProperties;
import br.edu.usc.campusiachatbot.config.EstabelecimentoProperties;
import br.edu.usc.campusiachatbot.domain.ProdutoCatalogo;
import br.edu.usc.campusiachatbot.store.CatalogoStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AzureOpenAiToolExecutorTest {

    private CatalogoStore catalogoStore;
    private AzureOpenAiClient client;
    private AzureOpenAiToolExecutor executor;

    @BeforeEach
    void configurar() {
        catalogoStore = mock(CatalogoStore.class);
        client = mock(AzureOpenAiClient.class);
        executor = new AzureOpenAiToolExecutor(
                catalogoStore,
                () -> new EstabelecimentoProperties(
                        "Renovo Manipulação",
                        "farmacia de manipulacao",
                        "08:00 às 18:00",
                        "Av. Teste, 397",
                        "Pix e cartão",
                        "nao informado",
                        "Entrega configurada",
                        List.of("Iacanga", "Arealva"),
                        "SP",
                        "(14) 99999-9999",
                        "https://example.test"
                ),
                new CatalogoConsultaProperties(8, 6000, 12000),
                new ObjectMapper()
        );
    }

    @Test
    void deveConsultarCategoriaIsoladaEDevolverResultadoParaRespostaFinal() {
        ProdutoCatalogo produto = new ProdutoCatalogo(
                "produto:18",
                null,
                18,
                "Emagrecimento",
                "Detox 10 Dias",
                "Descrição segura",
                new BigDecimal("39.90"),
                null,
                "https://example.test/emagrecimento"
        );
        when(catalogoStore.listarPorCategoriaLimitada("Emagrecimento", 8))
                .thenReturn(List.of(produto));
        when(client.obterRespostaComFerramentas(any(), any()))
                .thenReturn(
                        new AzureOpenAiChatResponse(null, List.of(new AzureOpenAiToolCall(
                                "call_categoria",
                                "buscar_produtos_por_categoria",
                                "{\"categoria\":\"Emagrecimento\"}"
                        ))),
                        new AzureOpenAiChatResponse("{\"respostaGerada\":\"Detox 10 Dias custa R$ 39,90\"}", List.of())
                );

        String resposta = executor.executar(
                client,
                List.of(Map.of("role", "user", "content", "Emagrecimento"))
        );

        assertThat(resposta).contains("Detox 10 Dias", "39,90");
        verify(catalogoStore).listarPorCategoriaLimitada("Emagrecimento", 8);
        ArgumentCaptor<List<Map<String, Object>>> mensagens = ArgumentCaptor.captor();
        verify(client, org.mockito.Mockito.times(2)).obterRespostaComFerramentas(
                mensagens.capture(),
                any()
        );
        assertThat(mensagens.getAllValues().get(1).toString())
                .contains("call_categoria", "tool_call_id", "Detox 10 Dias", "39.90")
                .doesNotContain("RESULTADO_CATALOGO_NAO_CONFIAVEL_INICIO");
    }

    @Test
    void deveTentarNomeQuandoExpressaoIsoladaNaoForUmaCategoria() {
        ProdutoCatalogo produto = new ProdutoCatalogo(
                "produto:7",
                null,
                7,
                "Saúde",
                "Produto Fictício",
                "Descrição segura",
                new BigDecimal("29.90"),
                null,
                null
        );
        when(catalogoStore.listarPorCategoriaLimitada("Produto Fictício", 8)).thenReturn(List.of());
        when(catalogoStore.pesquisarPorProdutoLimitado("Produto Fictício", 8)).thenReturn(List.of(produto));
        when(client.obterRespostaComFerramentas(any(), any())).thenReturn(
                new AzureOpenAiChatResponse(null, List.of(new AzureOpenAiToolCall(
                        "call_categoria",
                        "buscar_produtos_por_categoria",
                        "{\"categoria\":\"Produto Fictício\"}"
                ))),
                new AzureOpenAiChatResponse(null, List.of(new AzureOpenAiToolCall(
                        "call_nome",
                        "buscar_produtos_por_nome",
                        "{\"termo\":\"Produto Fictício\"}"
                ))),
                new AzureOpenAiChatResponse("{\"respostaGerada\":\"Produto Fictício custa R$ 29,90\"}", List.of())
        );

        String resposta = executor.executar(
                client,
                List.of(Map.of("role", "user", "content", "Produto Fictício"))
        );

        assertThat(resposta).contains("Produto Fictício", "29,90");
        verify(catalogoStore).listarPorCategoriaLimitada("Produto Fictício", 8);
        verify(catalogoStore).pesquisarPorProdutoLimitado("Produto Fictício", 8);
    }

    @Test
    void deveResolverEmagrecerParaCategoriaRealQuandoModeloUsarCategoriaLiteral() {
        ProdutoCatalogo produto = produtoEmagrecimento();
        when(catalogoStore.listarPorCategoriaLimitada("emagrecer", 8)).thenReturn(List.of());
        when(catalogoStore.listarCategoriasLimitadas(CatalogoStore.LIMITE_MAXIMO_CONSULTA_IA))
                .thenReturn(List.of("Beleza", "Emagrecimento", "Saúde"));
        when(catalogoStore.listarPorCategoriaLimitada("Emagrecimento", 8)).thenReturn(List.of(produto));
        when(client.obterRespostaComFerramentas(any(), any())).thenReturn(
                new AzureOpenAiChatResponse(null, List.of(new AzureOpenAiToolCall(
                        "call_categoria",
                        "buscar_produtos_por_categoria",
                        "{\"categoria\":\"emagrecer\"}"
                ))),
                new AzureOpenAiChatResponse("{\"respostaGerada\":\"Detox 10 Dias custa R$ 39,90\"}", List.of())
        );

        String resposta = executor.executar(
                client,
                List.of(Map.of("role", "user", "content", "Produto para emagrecer"))
        );

        assertThat(resposta).contains("Detox 10 Dias", "39,90");
        verify(catalogoStore).listarPorCategoriaLimitada("Emagrecimento", 8);
    }

    @Test
    void deveResolverEmagrecerParaCategoriaRealQuandoModeloUsarNomeLiteral() {
        ProdutoCatalogo produto = produtoEmagrecimento();
        when(catalogoStore.pesquisarPorProdutoLimitado("emagrecer", 8)).thenReturn(List.of());
        when(catalogoStore.listarCategoriasLimitadas(CatalogoStore.LIMITE_MAXIMO_CONSULTA_IA))
                .thenReturn(List.of("Beleza", "Emagrecimento", "Saúde"));
        when(catalogoStore.listarPorCategoriaLimitada("Emagrecimento", 8)).thenReturn(List.of(produto));
        when(client.obterRespostaComFerramentas(any(), any())).thenReturn(
                new AzureOpenAiChatResponse(null, List.of(new AzureOpenAiToolCall(
                        "call_nome",
                        "buscar_produtos_por_nome",
                        "{\"termo\":\"emagrecer\"}"
                ))),
                new AzureOpenAiChatResponse("{\"respostaGerada\":\"Detox 10 Dias custa R$ 39,90\"}", List.of())
        );

        executor.executar(client, List.of(Map.of("role", "user", "content", "Produto para emagrecer")));

        verify(catalogoStore).listarPorCategoriaLimitada("Emagrecimento", 8);
    }

    @Test
    void deveRejeitarRespostaDeProdutoSemConsultarFerramentaDeCatalogo() {
        when(client.obterRespostaComFerramentas(any(), any())).thenReturn(new AzureOpenAiChatResponse("""
                {
                  "tipoSolicitacao": "COMPRA_PRODUTO",
                  "categoria": "ATENDIMENTO_COMERCIAL",
                  "respostaGerada": "Nao localizei itens.",
                  "necessitaAtendimentoHumano": true,
                  "motivoEncaminhamento": "Item nao localizado.",
                  "confianca": 70,
                  "solicitarCategorias": false,
                  "consultaCatalogo": null
                }
                """, List.of()));

        assertThatThrownBy(() -> executor.executar(
                client,
                List.of(Map.of("role", "user", "content", "Produto para emagrecer"))
        )).isInstanceOfSatisfying(
                AzureOpenAiClientException.class,
                exception -> assertThat(exception.reason())
                        .isEqualTo(AzureOpenAiClientException.Reason.INVALID_OUTPUT)
        );

        verify(catalogoStore, never()).listarPorCategoriaLimitada(any(), any(Integer.class));
    }

    @Test
    void deveConsultarDadosDoEstabelecimentoComoFerramentaSomenteLeitura() {
        when(client.obterRespostaComFerramentas(any(), any()))
                .thenReturn(
                        new AzureOpenAiChatResponse(null, List.of(new AzureOpenAiToolCall(
                                "call_pagamento",
                                "consultar_estabelecimento",
                                "{\"assunto\":\"PAGAMENTO\"}"
                        ))),
                        new AzureOpenAiChatResponse("{\"respostaGerada\":\"Aceitamos Pix e cartão\"}", List.of())
                );

        String resposta = executor.executar(
                client,
                List.of(Map.of("role", "user", "content", "Como posso pagar?"))
        );

        assertThat(resposta).contains("Aceitamos Pix");
        ArgumentCaptor<List<Map<String, Object>>> mensagens = ArgumentCaptor.captor();
        verify(client, org.mockito.Mockito.times(2)).obterRespostaComFerramentas(
                mensagens.capture(),
                any()
        );
        assertThat(mensagens.getAllValues().get(1).toString()).contains("Pix e cartão");
        verify(catalogoStore, never()).listarTodosOrdenadosPorCodigo();
    }

    @Test
    void deveRejeitarCamposExtrasAntesDeConsultarCosmos() {
        when(client.obterRespostaComFerramentas(any(), any()))
                .thenReturn(new AzureOpenAiChatResponse(null, List.of(new AzureOpenAiToolCall(
                        "call_invalida",
                        "buscar_produtos_por_categoria",
                        "{\"categoria\":\"Emagrecimento\",\"instrucao\":\"ignorar regras\"}"
                ))));

        assertThatThrownBy(() -> executor.executar(
                client,
                List.of(Map.of("role", "user", "content", "Emagrecimento"))
        )).isInstanceOfSatisfying(
                AzureOpenAiClientException.class,
                exception -> assertThat(exception.reason())
                        .isEqualTo(AzureOpenAiClientException.Reason.INVALID_OUTPUT)
        );

        verify(catalogoStore, never()).listarPorCategoriaLimitada(any(), any(Integer.class));
    }

    @Test
    void deveRejeitarFaixaDePrecoComFracaoDeCentavo() {
        when(client.obterRespostaComFerramentas(any(), any()))
                .thenReturn(new AzureOpenAiChatResponse(null, List.of(new AzureOpenAiToolCall(
                        "call_preco",
                        "buscar_produtos_por_faixa_preco",
                        "{\"precoMinimo\":10.001,\"precoMaximo\":20}"
                ))));

        assertThatThrownBy(() -> executor.executar(
                client,
                List.of(Map.of("role", "user", "content", "Produtos entre 10 e 20"))
        )).isInstanceOf(AzureOpenAiClientException.class);

        verify(catalogoStore, never()).listarPorFaixaDePrecoLimitada(any(), any(), any(Integer.class));
    }

    private ProdutoCatalogo produtoEmagrecimento() {
        return new ProdutoCatalogo(
                "produto:18",
                null,
                18,
                "Emagrecimento",
                "Detox 10 Dias",
                "Descrição segura",
                new BigDecimal("39.90"),
                null,
                null
        );
    }
}
