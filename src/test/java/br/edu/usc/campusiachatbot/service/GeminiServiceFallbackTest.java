package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.client.GeminiClient;
import br.edu.usc.campusiachatbot.config.GeminiProperties;
import br.edu.usc.campusiachatbot.config.CatalogoConsultaProperties;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.EnderecoEnriquecidoDTO;
import br.edu.usc.campusiachatbot.dto.InterpretacaoIaResponseDTO;
import br.edu.usc.campusiachatbot.enums.TipoSolicitacaoEnum;
import br.edu.usc.campusiachatbot.exception.GeminiIntegrationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.edu.usc.campusiachatbot.store.CatalogoStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeminiServiceFallbackTest {

    @Test
    void deveUsarClassificacaoLocalQuandoGeminiEstiverIndisponivel() {
        GeminiClient geminiClient = mock(GeminiClient.class);
        PromptBuilderService promptBuilderService = mock(PromptBuilderService.class);
        GeminiProperties properties = new GeminiProperties(
                "chave-configurada",
                "gemini-2.5-flash",
                "https://generativelanguage.googleapis.com",
                5
        );
        CatalogoInterpretacaoOrchestrator orchestrator = new CatalogoInterpretacaoOrchestrator(
                promptBuilderService,
                new LocalInterpretacaoService(),
                mock(CatalogoStore.class),
                new CatalogoConsultaProperties(8, 6000, 12000),
                new ObjectMapper()
        );
        GeminiService geminiService = new GeminiService(properties, geminiClient, orchestrator);

        when(promptBuilderService.construirContents(any(), any(), any())).thenReturn(List.of());
        when(geminiClient.obterRespostaEstruturada(any()))
                .thenThrow(new GeminiIntegrationException("falha simulada", null));

        InterpretacaoIaResponseDTO resposta = geminiService.interpretarMensagem(
                new ChatbotRequestDTO(
                        "14999999999",
                        "Teste",
                        "Qual e o horario de funcionamento?",
                        null
                ),
                EnderecoEnriquecidoDTO.vazio(),
                List.of()
        );

        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.HORARIO_FUNCIONAMENTO);
        assertThat(resposta.respostaGerada()).isNotBlank();
    }
}
