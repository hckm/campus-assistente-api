package br.edu.usc.campusiachatbot.client;

import br.edu.usc.campusiachatbot.service.InterpretacaoIaSchema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AzureOpenAiClient {

    private final HttpClient httpClient;
    private final URI chatCompletionsUri;
    private final String deployment;
    private final Duration timeout;
    private final int maxOutputTokens;
    private final int maxResponseBytes;
    private final AzureOpenAiAuthentication authentication;
    private final ObjectMapper objectMapper;

    public AzureOpenAiClient(
            HttpClient httpClient,
            URI chatCompletionsUri,
            String deployment,
            Duration timeout,
            int maxOutputTokens,
            int maxResponseBytes,
            AzureOpenAiAuthentication authentication,
            ObjectMapper objectMapper
    ) {
        this.httpClient = httpClient;
        this.chatCompletionsUri = chatCompletionsUri;
        this.deployment = deployment;
        this.timeout = timeout;
        this.maxOutputTokens = maxOutputTokens;
        this.maxResponseBytes = maxResponseBytes;
        this.authentication = authentication;
        this.objectMapper = objectMapper;
    }

    public String obterRespostaEstruturada(List<Map<String, Object>> messages) {
        AzureOpenAiChatResponse response = executar(messages, List.of());
        if (response.hasToolCalls() || response.content() == null || response.content().isBlank()) {
            throw respostaInvalida();
        }
        return response.content();
    }

    public AzureOpenAiChatResponse obterRespostaComFerramentas(
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools
    ) {
        if (tools == null || tools.isEmpty()) {
            throw new IllegalArgumentException("Ao menos uma ferramenta deve ser informada");
        }
        return executar(messages, tools);
    }

    private AzureOpenAiChatResponse executar(
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools
    ) {
        long deadline = System.nanoTime() + timeout.toNanos();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(chatCompletionsUri)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        serializarRequisicao(messages, tools),
                        StandardCharsets.UTF_8
                ));
        authentication.apply(requestBuilder);
        requestBuilder.timeout(tempoRestante(deadline));

        CompletableFuture<HttpResponse<byte[]>> responseFuture = httpClient.sendAsync(
                requestBuilder.build(),
                responseInfo -> new LimitedBodySubscriber(maxResponseBytes)
        );
        try {
            Duration remaining = tempoRestante(deadline);
            HttpResponse<byte[]> response = responseFuture.get(remaining.toNanos(), TimeUnit.NANOSECONDS);
            String body = new String(response.body(), StandardCharsets.UTF_8);
            if (response.statusCode() != 200) {
                throw mapearErroHttp(response.statusCode(), body);
            }
            return extrairResposta(body);
        } catch (TimeoutException exception) {
            responseFuture.cancel(true);
            throw new AzureOpenAiClientException(
                    AzureOpenAiClientException.Reason.TIMEOUT,
                    "A chamada ao Azure OpenAI excedeu o tempo limite",
                    exception
            );
        } catch (InterruptedException exception) {
            responseFuture.cancel(true);
            Thread.currentThread().interrupt();
            throw new AzureOpenAiClientException(
                    AzureOpenAiClientException.Reason.INTERRUPTED,
                    "A chamada ao Azure OpenAI foi interrompida",
                    exception
            );
        } catch (ExecutionException exception) {
            throw mapearFalhaAssincrona(exception.getCause());
        }
    }

    private AzureOpenAiClientException mapearFalhaAssincrona(Throwable cause) {
        if (causadoPorRespostaMuitoGrande(cause)) {
            return new AzureOpenAiClientException(
                    AzureOpenAiClientException.Reason.RESPONSE_TOO_LARGE,
                    "Resposta do Azure OpenAI excedeu o limite configurado",
                    cause
            );
        }
        if (causadoPorTimeout(cause)) {
            return new AzureOpenAiClientException(
                    AzureOpenAiClientException.Reason.TIMEOUT,
                    "A chamada ao Azure OpenAI excedeu o tempo limite",
                    cause
            );
        }
        return new AzureOpenAiClientException(
                AzureOpenAiClientException.Reason.PROVIDER_UNAVAILABLE,
                "Falha de comunicacao com Azure OpenAI",
                cause
        );
    }

    private Duration tempoRestante(long deadline) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) {
            throw new AzureOpenAiClientException(
                    AzureOpenAiClientException.Reason.TIMEOUT,
                    "A chamada ao Azure OpenAI excedeu o tempo limite"
            );
        }
        return Duration.ofNanos(nanos);
    }

    private String serializarRequisicao(
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", deployment);
        request.put("messages", messages);
        request.put("max_completion_tokens", maxOutputTokens);
        request.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "interpretacao_atendimento",
                        "strict", true,
                        "schema", InterpretacaoIaSchema.criar()
                )
        ));
        if (!tools.isEmpty()) {
            request.put("tools", tools);
            request.put("tool_choice", "auto");
            request.put("parallel_tool_calls", false);
        }
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new AzureOpenAiClientException(
                    AzureOpenAiClientException.Reason.INVALID_OUTPUT,
                    "Nao foi possivel serializar a requisicao Azure OpenAI",
                    exception
            );
        }
    }

    private AzureOpenAiChatResponse extrairResposta(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choice = root.path("choices").path(0);
            String finishReason = choice.path("finish_reason").asText("");
            JsonNode message = choice.path("message");
            String refusal = message.path("refusal").asText("");
            if (!refusal.isBlank()) {
                throw new AzureOpenAiClientException(
                        AzureOpenAiClientException.Reason.REFUSAL,
                        "Azure OpenAI recusou a solicitacao"
                );
            }
            if ("content_filter".equals(finishReason)) {
                throw new AzureOpenAiClientException(
                        AzureOpenAiClientException.Reason.CONTENT_FILTER,
                        "Azure OpenAI bloqueou a resposta pelo filtro de conteudo"
                );
            }
            if ("length".equals(finishReason)) {
                throw new AzureOpenAiClientException(
                        AzureOpenAiClientException.Reason.TRUNCATED_OUTPUT,
                        "Azure OpenAI encerrou a resposta por limite de tokens"
                );
            }
            if ("tool_calls".equals(finishReason)) {
                return extrairFerramentas(message.path("tool_calls"));
            }
            if (!"stop".equals(finishReason)) {
                throw respostaInvalida();
            }
            JsonNode content = message.path("content");
            if (!content.isTextual() || content.textValue().isBlank()) {
                throw respostaInvalida();
            }
            return new AzureOpenAiChatResponse(content.textValue(), List.of());
        } catch (AzureOpenAiClientException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new AzureOpenAiClientException(
                    AzureOpenAiClientException.Reason.INVALID_OUTPUT,
                    "Resposta HTTP do Azure OpenAI nao contem JSON valido",
                    exception
            );
        }
    }

    private AzureOpenAiChatResponse extrairFerramentas(JsonNode toolCallsNode) {
        if (!toolCallsNode.isArray() || toolCallsNode.isEmpty()) {
            throw respostaInvalida();
        }
        List<AzureOpenAiToolCall> toolCalls = new ArrayList<>();
        for (JsonNode toolCallNode : toolCallsNode) {
            String id = toolCallNode.path("id").asText("");
            String type = toolCallNode.path("type").asText("");
            JsonNode function = toolCallNode.path("function");
            String name = function.path("name").asText("");
            String arguments = function.path("arguments").asText("");
            if (id.isBlank()
                    || !"function".equals(type)
                    || name.isBlank()
                    || arguments.isBlank()) {
                throw respostaInvalida();
            }
            toolCalls.add(new AzureOpenAiToolCall(id, name, arguments));
        }
        return new AzureOpenAiChatResponse(null, toolCalls);
    }

    private AzureOpenAiClientException mapearErroHttp(int statusCode, String body) {
        if (contemCodigoFiltroDeConteudo(body)) {
            return new AzureOpenAiClientException(
                    AzureOpenAiClientException.Reason.CONTENT_FILTER,
                    "Azure OpenAI bloqueou a solicitacao pelo filtro de conteudo"
            );
        }
        return switch (statusCode) {
            case 401 -> new AzureOpenAiClientException(
                    AzureOpenAiClientException.Reason.AUTHENTICATION,
                    "Azure OpenAI rejeitou a autenticacao"
            );
            case 403 -> new AzureOpenAiClientException(
                    AzureOpenAiClientException.Reason.AUTHORIZATION,
                    "A identidade nao tem permissao para usar Azure OpenAI"
            );
            case 429 -> new AzureOpenAiClientException(
                    AzureOpenAiClientException.Reason.RATE_LIMIT,
                    "Azure OpenAI atingiu o limite de requisicoes"
            );
            default -> statusCode >= 500
                    ? new AzureOpenAiClientException(
                            AzureOpenAiClientException.Reason.PROVIDER_UNAVAILABLE,
                            "Azure OpenAI esta temporariamente indisponivel"
                    )
                    : new AzureOpenAiClientException(
                            AzureOpenAiClientException.Reason.PROVIDER_FAILURE,
                            "Azure OpenAI rejeitou a requisicao"
                    );
        };
    }

    private boolean contemCodigoFiltroDeConteudo(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String code = root.path("error").path("code").asText("");
            return "content_filter".equalsIgnoreCase(code)
                    || "contentfilter".equalsIgnoreCase(code);
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private boolean causadoPorRespostaMuitoGrande(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ResponseTooLargeException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean causadoPorTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private AzureOpenAiClientException respostaInvalida() {
        return new AzureOpenAiClientException(
                AzureOpenAiClientException.Reason.INVALID_OUTPUT,
                "Resposta do Azure OpenAI nao corresponde ao contrato esperado"
        );
    }

    private static final class LimitedBodySubscriber implements BodySubscriber<byte[]> {

        private final int maxBytes;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private LimitedBodySubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                int remaining = buffer.remaining();
                if (remaining > maxBytes - output.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new ResponseTooLargeException());
                    return;
                }
                byte[] bytes = new byte[remaining];
                buffer.get(bytes);
                output.writeBytes(bytes);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(output.toByteArray());
        }
    }

    private static final class ResponseTooLargeException extends IOException {
    }
}
