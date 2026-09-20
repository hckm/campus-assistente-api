package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.client.AzureOpenAiClient;
import br.edu.usc.campusiachatbot.client.AzureOpenAiClientException;
import br.edu.usc.campusiachatbot.domain.MensagemConversa;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.EnderecoEnriquecidoDTO;
import br.edu.usc.campusiachatbot.dto.InterpretacaoIaResponseDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AzureOpenAiInterpretacaoService implements InterpretacaoIaService {

    private final AzureOpenAiClient client;
    private final CatalogoInterpretacaoOrchestrator orchestrator;
    private final AzureOpenAiToolExecutor toolExecutor;

    public AzureOpenAiInterpretacaoService(
            AzureOpenAiClient client,
            CatalogoInterpretacaoOrchestrator orchestrator,
            AzureOpenAiToolExecutor toolExecutor
    ) {
        this.client = client;
        this.orchestrator = orchestrator;
        this.toolExecutor = toolExecutor;
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
                contents -> toolExecutor.executar(client, converterMensagens(contents)),
                "AZURE_OPENAI",
                true
        );
    }

    private List<Map<String, Object>> converterMensagens(List<Map<String, Object>> contents) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Map<String, Object> content : contents) {
            String role = converterRole(content.get("role"));
            String texto = extrairTexto(content.get("parts"));
            messages.add(Map.of("role", role, "content", texto));
        }
        return List.copyOf(messages);
    }

    private String converterRole(Object role) {
        if ("user".equals(role)) {
            return "user";
        }
        if ("model".equals(role)) {
            return "assistant";
        }
        throw respostaInvalida();
    }

    private String extrairTexto(Object partsValue) {
        if (!(partsValue instanceof List<?> parts) || parts.isEmpty()) {
            throw respostaInvalida();
        }
        StringBuilder texto = new StringBuilder();
        for (Object partValue : parts) {
            if (!(partValue instanceof Map<?, ?> part) || !(part.get("text") instanceof String partText)) {
                throw respostaInvalida();
            }
            if (!texto.isEmpty()) {
                texto.append('\n');
            }
            texto.append(partText);
        }
        if (texto.toString().isBlank()) {
            throw respostaInvalida();
        }
        return texto.toString();
    }

    private AzureOpenAiClientException respostaInvalida() {
        return new AzureOpenAiClientException(
                AzureOpenAiClientException.Reason.INVALID_OUTPUT,
                "Resposta do Azure OpenAI nao corresponde ao contrato esperado"
        );
    }
}
