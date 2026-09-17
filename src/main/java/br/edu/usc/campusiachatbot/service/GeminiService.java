package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.client.GeminiClient;
import br.edu.usc.campusiachatbot.config.GeminiProperties;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.EnderecoEnriquecidoDTO;
import br.edu.usc.campusiachatbot.dto.InterpretacaoIaResponseDTO;
import br.edu.usc.campusiachatbot.domain.MensagemConversa;
import br.edu.usc.campusiachatbot.exception.GeminiIntegrationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "ia", name = "provider", havingValue = "GEMINI", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class GeminiService implements InterpretacaoIaService {

    private final GeminiProperties properties;
    private final GeminiClient geminiClient;
    private final CatalogoInterpretacaoOrchestrator orchestrator;

    public InterpretacaoIaResponseDTO interpretarMensagem(ChatbotRequestDTO request) {
        return interpretarMensagem(request, EnderecoEnriquecidoDTO.vazio(), List.of());
    }

    public InterpretacaoIaResponseDTO interpretarMensagem(
            ChatbotRequestDTO request,
            EnderecoEnriquecidoDTO enderecoEnriquecido) {
        return interpretarMensagem(request, enderecoEnriquecido, List.of());
    }

    @Override
    public InterpretacaoIaResponseDTO interpretarMensagem(
            ChatbotRequestDTO request,
            EnderecoEnriquecidoDTO enderecoEnriquecido,
            List<MensagemConversa> historico) {

        return orchestrator.interpretar(
                request,
                enderecoEnriquecido,
                historico,
                this::inferir,
                "GEMINI"
        );
    }

    private String inferir(List<Map<String, Object>> contents) {
        if (!properties.hasApiKey()) {
            throw new GeminiIntegrationException("Chave do Gemini nao configurada", null);
        }
        log.debug("Enviando {} turno(s) ao Gemini para o atendimento", contents.size());
        return geminiClient.obterRespostaEstruturada(contents);
    }

}
