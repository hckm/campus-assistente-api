package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.domain.MensagemConversa;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.EnderecoEnriquecidoDTO;
import br.edu.usc.campusiachatbot.dto.InterpretacaoIaResponseDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public class CodexCliInterpretacaoService implements InterpretacaoIaService {

    private final CodexCliExecutor executor;
    private final CatalogoInterpretacaoOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    public CodexCliInterpretacaoService(
            CodexCliExecutor executor,
            CatalogoInterpretacaoOrchestrator orchestrator,
            ObjectMapper objectMapper
    ) {
        this.executor = executor;
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
    }

    @Override
    public InterpretacaoIaResponseDTO interpretarMensagem(
            ChatbotRequestDTO request,
            EnderecoEnriquecidoDTO enderecoEnriquecido,
            List<MensagemConversa> historico
    ) {
        return orchestrator.interpretar(
                request,
                enderecoEnriquecido,
                historico,
                contents -> executor.executar(construirPrompt(contents)),
                "CODEX_CLI"
        );
    }

    private String construirPrompt(List<Map<String, Object>> contents) {
        try {
            return """
                    Interprete a conversa estruturada a seguir segundo todas as regras contidas nela.
                    Produza somente o objeto definido pelo JSON Schema da execucao.
                    Nao solicite ferramentas nem execute comandos.

                    %s
                    """.formatted(objectMapper.writeValueAsString(contents));
        } catch (JsonProcessingException exception) {
            throw new CodexCliException(
                    CodexCliException.Reason.INVALID_OUTPUT,
                    "Nao foi possivel serializar o contexto para o Codex CLI",
                    exception
            );
        }
    }

}
