package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.domain.MensagemConversa;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.EnderecoEnriquecidoDTO;
import br.edu.usc.campusiachatbot.dto.InterpretacaoIaResponseDTO;
import br.edu.usc.campusiachatbot.enums.CategoriaAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.DirecaoMensagemEnum;
import br.edu.usc.campusiachatbot.enums.TipoSolicitacaoEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodexCliInterpretacaoServiceTest {

    private final CodexCliExecutor executor = mock(CodexCliExecutor.class);
    private final CatalogoInterpretacaoOrchestrator orchestrator = mock(CatalogoInterpretacaoOrchestrator.class);
    private final CodexCliInterpretacaoService service = new CodexCliInterpretacaoService(
            executor,
            orchestrator,
            new ObjectMapper()
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
    void deveAceitarJsonValidoEReutilizarHistoricoEstruturado() {
        when(executor.executar(any())).thenReturn("""
                {
                  "tipoSolicitacao": "COMPRA_PRODUTO",
                  "categoria": "ATENDIMENTO_COMERCIAL",
                  "respostaGerada": "Informe a quantidade desejada.",
                  "necessitaAtendimentoHumano": false,
                  "motivoEncaminhamento": null,
                  "confianca": 0.91
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
        when(orchestrator.interpretar(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            InferenciaIa inferencia = invocation.getArgument(3);
            inferencia.executar(List.of(
                    Map.of("role", "user", "parts", List.of(Map.of("text", "contexto seguro"))),
                    Map.of("role", "model", "parts", List.of(Map.of("text", "resposta anterior")))
            ));
            return esperada;
        });

        InterpretacaoIaResponseDTO resposta = service.interpretarMensagem(
                request,
                EnderecoEnriquecidoDTO.vazio(),
                List.of(new MensagemConversa(DirecaoMensagemEnum.CLIENTE, request.mensagem()))
        );

        assertThat(resposta).isEqualTo(esperada);
        ArgumentCaptor<String> prompt = ArgumentCaptor.captor();
        verify(executor).executar(prompt.capture());
        assertThat(prompt.getValue()).contains("contexto seguro", "resposta anterior");
    }

    @Test
    void deveUsarFallbackLocalQuandoJsonForInvalido() {
        InterpretacaoIaResponseDTO fallback = fallback();
        when(executor.executar(any())).thenReturn("{\"tipoSolicitacao\":\"OUTROS\"}");
        when(orchestrator.interpretar(any(), any(), any(), any(), any())).thenReturn(fallback);

        InterpretacaoIaResponseDTO resposta = service.interpretarMensagem(
                request,
                EnderecoEnriquecidoDTO.vazio(),
                List.of()
        );

        assertThat(resposta).isEqualTo(fallback);
        verify(orchestrator).interpretar(any(), any(), any(), any(), any());
    }

    @Test
    void deveUsarFallbackLocalQuandoProcessoFalhar() {
        InterpretacaoIaResponseDTO fallback = fallback();
        when(executor.executar(any())).thenThrow(new CodexCliException(
                CodexCliException.Reason.PROCESS_FAILURE,
                "falha simulada"
        ));
        when(orchestrator.interpretar(any(), any(), any(), any(), any())).thenReturn(fallback);

        InterpretacaoIaResponseDTO resposta = service.interpretarMensagem(
                request,
                EnderecoEnriquecidoDTO.vazio(),
                List.of()
        );

        assertThat(resposta).isEqualTo(fallback);
    }

    @Test
    void deveUsarFallbackLocalQuandoProcessoExcederTimeout() {
        InterpretacaoIaResponseDTO fallback = fallback();
        when(executor.executar(any())).thenThrow(new CodexCliException(
                CodexCliException.Reason.TIMEOUT,
                "timeout simulado"
        ));
        when(orchestrator.interpretar(any(), any(), any(), any(), any())).thenReturn(fallback);

        InterpretacaoIaResponseDTO resposta = service.interpretarMensagem(
                request,
                EnderecoEnriquecidoDTO.vazio(),
                List.of()
        );

        assertThat(resposta).isEqualTo(fallback);
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
