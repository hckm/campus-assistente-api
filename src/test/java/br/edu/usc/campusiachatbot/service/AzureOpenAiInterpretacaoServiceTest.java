package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.client.AzureOpenAiClient;
import br.edu.usc.campusiachatbot.client.AzureOpenAiClientException;
import br.edu.usc.campusiachatbot.client.AzureOpenAiChatResponse;
import br.edu.usc.campusiachatbot.client.AzureOpenAiToolCall;
import br.edu.usc.campusiachatbot.config.CatalogoConsultaProperties;
import br.edu.usc.campusiachatbot.config.EstabelecimentoProperties;
import br.edu.usc.campusiachatbot.domain.MensagemConversa;
import br.edu.usc.campusiachatbot.domain.ProdutoCatalogo;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.EnderecoEnriquecidoDTO;
import br.edu.usc.campusiachatbot.dto.InterpretacaoIaResponseDTO;
import br.edu.usc.campusiachatbot.enums.CategoriaAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.DirecaoMensagemEnum;
import br.edu.usc.campusiachatbot.enums.TipoSolicitacaoEnum;
import br.edu.usc.campusiachatbot.store.CatalogoStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AzureOpenAiInterpretacaoServiceTest {

    private final AzureOpenAiClient client = mock(AzureOpenAiClient.class);
    private final CatalogoInterpretacaoOrchestrator orchestrator = mock(CatalogoInterpretacaoOrchestrator.class);
    private final AzureOpenAiToolExecutor toolExecutor = mock(AzureOpenAiToolExecutor.class);
    private final AzureOpenAiInterpretacaoService service = new AzureOpenAiInterpretacaoService(
            client,
            orchestrator,
            toolExecutor
    );
    private final ChatbotRequestDTO request = new ChatbotRequestDTO(
            "14999999999",
            "Cliente Ficticio",
            "Quero consultar um produto ficticio",
            null
    );

    @BeforeEach
    void configurarPrompt() {
    }

    @Test
    void deveAceitarContratoEstritoEConverterHistoricoParaChatCompletions() {
        when(toolExecutor.executar(any(), any())).thenReturn("""
                {
                  "tipoSolicitacao": "COMPRA_PRODUTO",
                  "categoria": "ATENDIMENTO_COMERCIAL",
                  "respostaGerada": "Informe a quantidade desejada.",
                  "necessitaAtendimentoHumano": false,
                  "motivoEncaminhamento": null,
                  "confianca": 91
                }
                """);
        InterpretacaoIaResponseDTO esperada = new InterpretacaoIaResponseDTO(
                TipoSolicitacaoEnum.COMPRA_PRODUTO,
                CategoriaAtendimentoEnum.ATENDIMENTO_COMERCIAL,
                "Informe a quantidade desejada.",
                false,
                null,
                91.0
        );
        when(orchestrator.interpretar(any(), any(), any(), any(), any(), any(Boolean.class))).thenAnswer(invocation -> {
            InferenciaIa inferencia = invocation.getArgument(3);
            inferencia.executar(List.of(
                    Map.of("role", "user", "parts", List.of(Map.of("text", "contexto seguro"))),
                    Map.of("role", "model", "parts", List.of(Map.of("text", "resposta anterior"))),
                    Map.of("role", "user", "parts", List.of(Map.of("text", "mensagem atual")))
            ));
            return esperada;
        });

        InterpretacaoIaResponseDTO resposta = service.interpretarMensagem(
                request,
                EnderecoEnriquecidoDTO.vazio(),
                List.of(new MensagemConversa(DirecaoMensagemEnum.CLIENTE, request.mensagem()))
        );

        assertThat(resposta).isEqualTo(esperada);
        ArgumentCaptor<List<Map<String, Object>>> messages = ArgumentCaptor.captor();
        verify(toolExecutor).executar(any(), messages.capture());
        assertThat(messages.getValue()).containsExactly(
                Map.of("role", "user", "content", "contexto seguro"),
                Map.of("role", "assistant", "content", "resposta anterior"),
                Map.of("role", "user", "content", "mensagem atual")
        );
    }

    @Test
    void deveUsarFallbackLocalQuandoRespostaViolarContrato() {
        InterpretacaoIaResponseDTO fallback = fallback();
        when(toolExecutor.executar(any(), any())).thenReturn("""
                {
                  "tipoSolicitacao": "OUTROS",
                  "categoria": "OUTROS",
                  "respostaGerada": "Resposta",
                  "necessitaAtendimentoHumano": false,
                  "motivoEncaminhamento": null,
                  "confianca": 80,
                  "campoInesperado": true
                }
                """);
        when(orchestrator.interpretar(any(), any(), any(), any(), any(), any(Boolean.class))).thenReturn(fallback);

        InterpretacaoIaResponseDTO resposta = service.interpretarMensagem(
                request,
                EnderecoEnriquecidoDTO.vazio(),
                List.of()
        );

        assertThat(resposta).isEqualTo(fallback);
        verify(orchestrator).interpretar(any(), any(), any(), any(), any(), any(Boolean.class));
    }

    @Test
    void deveUsarFallbackLocalQuandoAzureFalhar() {
        InterpretacaoIaResponseDTO fallback = fallback();
        when(toolExecutor.executar(any(), any())).thenThrow(new AzureOpenAiClientException(
                AzureOpenAiClientException.Reason.RATE_LIMIT,
                "falha simulada"
        ));
        when(orchestrator.interpretar(any(), any(), any(), any(), any(), any(Boolean.class))).thenReturn(fallback);

        InterpretacaoIaResponseDTO resposta = service.interpretarMensagem(
                request,
                EnderecoEnriquecidoDTO.vazio(),
                List.of()
        );

        assertThat(resposta).isEqualTo(fallback);
    }

    @Test
    void deveTratarCategoriaIsoladaComFerramentaEConsultaRealDoStore() {
        CatalogoStore store = mock(CatalogoStore.class);
        CatalogoConsultaProperties properties = new CatalogoConsultaProperties(8, 6000, 12000);
        EstabelecimentoComercialProvider estabelecimento = () -> new EstabelecimentoProperties(
                "Renovo Manipulação",
                "farmacia de manipulacao",
                "08:00 às 18:00",
                "Av. Teste, 397",
                "Pix e cartão",
                null,
                "Entrega configurada",
                List.of("Iacanga"),
                "SP",
                "(14) 99999-9999",
                "https://example.test"
        );
        ObjectMapper mapper = new ObjectMapper();
        PromptBuilderService promptBuilder = new PromptBuilderService(estabelecimento);
        CatalogoInterpretacaoOrchestrator orchestratorReal = new CatalogoInterpretacaoOrchestrator(
                promptBuilder,
                new LocalInterpretacaoService(),
                store,
                properties,
                mapper
        );
        AzureOpenAiToolExecutor executorReal = new AzureOpenAiToolExecutor(
                store,
                estabelecimento,
                properties,
                mapper
        );
        AzureOpenAiClient clientReal = mock(AzureOpenAiClient.class);
        when(store.listarPorCategoriaLimitada("Emagrecimento", 8)).thenReturn(List.of(
                new ProdutoCatalogo(
                        "produto:18",
                        null,
                        18,
                        "Emagrecimento",
                        "Detox 10 Dias",
                        "Descrição segura",
                        new BigDecimal("39.90"),
                        null,
                        "https://example.test/emagrecimento"
                )
        ));
        when(clientReal.obterRespostaComFerramentas(any(), any())).thenReturn(
                new AzureOpenAiChatResponse(null, List.of(new AzureOpenAiToolCall(
                        "call_categoria",
                        "buscar_produtos_por_categoria",
                        "{\"categoria\":\"Emagrecimento\"}"
                ))),
                new AzureOpenAiChatResponse("""
                        {
                          "tipoSolicitacao": "COMPRA_PRODUTO",
                          "categoria": "ATENDIMENTO_COMERCIAL",
                          "respostaGerada": "Na categoria Emagrecimento temos Detox 10 Dias por R$ 39,90.",
                          "necessitaAtendimentoHumano": false,
                          "motivoEncaminhamento": null,
                          "confianca": 98,
                          "solicitarCategorias": false,
                          "consultaCatalogo": null
                        }
                        """, List.of())
        );
        AzureOpenAiInterpretacaoService serviceReal = new AzureOpenAiInterpretacaoService(
                clientReal,
                orchestratorReal,
                executorReal
        );
        ChatbotRequestDTO categoriaIsolada = new ChatbotRequestDTO(
                "14999999999",
                "Cliente",
                "Emagrecimento",
                null
        );

        InterpretacaoIaResponseDTO resposta = serviceReal.interpretarMensagem(
                categoriaIsolada,
                EnderecoEnriquecidoDTO.vazio(),
                List.of()
        );

        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.COMPRA_PRODUTO);
        assertThat(resposta.respostaGerada()).contains("Emagrecimento", "Detox 10 Dias", "39,90");
        assertThat(resposta.necessitaAtendimentoHumano()).isFalse();
        verify(store).listarPorCategoriaLimitada("Emagrecimento", 8);
    }

    private InterpretacaoIaResponseDTO fallback() {
        return new InterpretacaoIaResponseDTO(
                TipoSolicitacaoEnum.OUTROS,
                CategoriaAtendimentoEnum.OUTROS,
                "Atendimento local.",
                true,
                "Fallback local",
                55.0
        );
    }
}
