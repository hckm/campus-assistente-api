package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.client.GeminiClient;
import br.edu.usc.campusiachatbot.config.EstabelecimentoProperties;
import br.edu.usc.campusiachatbot.config.GeminiProperties;
import br.edu.usc.campusiachatbot.domain.MensagemConversa;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.EnderecoEnriquecidoDTO;
import br.edu.usc.campusiachatbot.dto.InterpretacaoIaResponseDTO;
import br.edu.usc.campusiachatbot.enums.CategoriaAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.DirecaoMensagemEnum;
import br.edu.usc.campusiachatbot.enums.TipoSolicitacaoEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiServiceAdaptadorTest {

    private final GeminiClient client = mock(GeminiClient.class);
    private final CatalogoInterpretacaoOrchestrator orchestrator = mock(CatalogoInterpretacaoOrchestrator.class);
    private final InterpretacaoIaService service = new GeminiService(
            new GeminiProperties("chave-simulada", "modelo-simulado", "https://example.invalid", 5),
            client,
            orchestrator);

    @Test
    void deveConverterHistoricoNeutroParaGeminiERetornarDtoNeutro() {
        ChatbotRequestDTO request = new ChatbotRequestDTO("14999999999", "Teste", "Quero prosseguir", null);
        List<MensagemConversa> historico = List.of(
                new MensagemConversa(DirecaoMensagemEnum.CLIENTE, "Quero um produto"),
                new MensagemConversa(DirecaoMensagemEnum.BOT, "Qual produto?"),
                new MensagemConversa(DirecaoMensagemEnum.CLIENTE, "Quero prosseguir"));
        InterpretacaoIaResponseDTO esperada = new InterpretacaoIaResponseDTO(
                TipoSolicitacaoEnum.COMPRA_PRODUTO, CategoriaAtendimentoEnum.ATENDIMENTO_COMERCIAL,
                "Vou encaminhar seu pedido.", true, "Confirmacao pela equipe", 92.0);
        when(orchestrator.interpretar(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            InferenciaIa inferencia = invocation.getArgument(3);
            inferencia.executar(List.of(
                    Map.of("role", "user", "parts", List.of(Map.of(
                            "text", "Voce nao pode prescrever medicamentos\nQuero um produto"))),
                    Map.of("role", "model", "parts", List.of(Map.of("text", "Qual produto?"))),
                    Map.of("role", "user", "parts", List.of(Map.of("text", "Quero prosseguir")))
            ));
            return esperada;
        });
        when(client.obterRespostaEstruturada(any())).thenReturn("""
                ```json
                {
                  "tipoSolicitacao": "COMPRA_PRODUTO",
                  "categoria": "ATENDIMENTO_COMERCIAL",
                  "respostaGerada": "Vou encaminhar seu pedido.",
                  "necessitaAtendimentoHumano": true,
                  "motivoEncaminhamento": "Confirmacao pela equipe",
                  "confianca": 0.92,
                  "campoAdicional": "ignorado"
                }
                ```
                """);

        InterpretacaoIaResponseDTO resposta = service.interpretarMensagem(
                request, EnderecoEnriquecidoDTO.vazio(), historico);

        assertThat(resposta).isEqualTo(new InterpretacaoIaResponseDTO(
                TipoSolicitacaoEnum.COMPRA_PRODUTO, CategoriaAtendimentoEnum.ATENDIMENTO_COMERCIAL,
                "Vou encaminhar seu pedido.", true, "Confirmacao pela equipe", 92.0));
        verify(orchestrator).interpretar(any(), any(), any(), any(), any());
        ArgumentCaptor<List<Map<String, Object>>> contents = ArgumentCaptor.captor();
        verify(client).obterRespostaEstruturada(contents.capture());
        assertThat(contents.getValue()).hasSize(3);
        assertThat(contents.getValue().get(1).get("role")).isEqualTo("model");
    }

    @Test
    void deveManterClassificacaoLocalSeguraQuandoJsonDoGeminiForInvalido() {
        when(orchestrator.interpretar(any(), any(), any(), any(), any())).thenReturn(
                new LocalInterpretacaoService().interpretar("Qual a dose?"));

        InterpretacaoIaResponseDTO resposta = service.interpretarMensagem(
                new ChatbotRequestDTO("14999999999", "Teste", "Qual a dose?", null),
                EnderecoEnriquecidoDTO.vazio(), List.of());

        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.DUVIDA_FARMACEUTICA);
        assertThat(resposta.necessitaAtendimentoHumano()).isTrue();
        assertThat(resposta.respostaGerada())
                .contains("avaliacao individual", "equipe farmaceutica", "farmaceutico")
                .doesNotContainIgnoringCase("humano", "humana");
    }
}
