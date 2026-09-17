package br.edu.usc.campusiachatbot.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AzureOpenAiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void pararServidor() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void deveEnviarChatCompletionV1ComSchemaEstritoEDeployment() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicInteger requests = new AtomicInteger();
        iniciarServidor(exchange -> {
            requests.incrementAndGet();
            requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
            apiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            responder(exchange, 200, respostaValida());
        });

        String content = client(Duration.ofSeconds(2), 65536).obterRespostaEstruturada(List.of(
                Map.of("role", "user", "content", "mensagem inicial"),
                Map.of("role", "assistant", "content", "resposta anterior"),
                Map.of("role", "user", "content", "mensagem atual")
        ));

        assertThat(requests).hasValue(1);
        assertThat(apiKey).hasValue("chave-teste");
        assertThat(content).contains("DUVIDA_ADMINISTRATIVA");
        JsonNode body = requestBody.get();
        assertThat(body.path("model").asText()).isEqualTo("deployment-teste");
        assertThat(body.path("max_completion_tokens").asInt()).isEqualTo(700);
        assertThat(body.path("messages").path(1).path("role").asText()).isEqualTo("assistant");
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_schema");
        JsonNode jsonSchema = body.path("response_format").path("json_schema");
        assertThat(jsonSchema.path("strict").asBoolean()).isTrue();
        assertThat(jsonSchema.path("schema").path("additionalProperties").asBoolean()).isFalse();
        assertThat(jsonSchema.path("schema").path("required")).hasSize(8);
        JsonNode consulta = jsonSchema.path("schema").path("properties").path("consultaCatalogo");
        assertThat(consulta.path("additionalProperties").asBoolean()).isFalse();
        assertThat(consulta.path("required")).hasSize(5);
        assertThat(jsonSchema.path("schema").path("properties").path("confianca").fieldNames())
                .toIterable()
                .containsExactly("type");
    }

    @ParameterizedTest
    @MethodSource("errosHttp")
    void deveClassificarErrosHttpSemRepetirChamada(
            int status,
            String body,
            AzureOpenAiClientException.Reason reason
    ) throws Exception {
        AtomicInteger requests = new AtomicInteger();
        iniciarServidor(exchange -> {
            requests.incrementAndGet();
            responder(exchange, status, body);
        });

        assertThatThrownBy(() -> client(Duration.ofSeconds(2), 65536)
                .obterRespostaEstruturada(List.of(Map.of("role", "user", "content", "teste"))))
                .isInstanceOfSatisfying(
                        AzureOpenAiClientException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(reason)
                );
        assertThat(requests).hasValue(1);
    }

    @ParameterizedTest
    @MethodSource("respostasInterrompidas")
    void deveRejeitarRespostaRecusadaFiltradaTruncadaOuInvalida(
            String body,
            AzureOpenAiClientException.Reason reason
    ) throws Exception {
        iniciarServidor(exchange -> responder(exchange, 200, body));

        assertThatThrownBy(() -> client(Duration.ofSeconds(2), 65536)
                .obterRespostaEstruturada(List.of(Map.of("role", "user", "content", "teste"))))
                .isInstanceOfSatisfying(
                        AzureOpenAiClientException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(reason)
                );
    }

    @Test
    void deveAplicarTimeoutSemRepetirChamada() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        iniciarServidor(exchange -> {
            requests.incrementAndGet();
            byte[] bytes = respostaValida().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try {
                Thread.sleep(500);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException exception) {
                exchange.close();
            }
        });

        assertThatThrownBy(() -> client(Duration.ofMillis(200), 65536)
                .obterRespostaEstruturada(List.of(Map.of("role", "user", "content", "teste"))))
                .isInstanceOfSatisfying(
                        AzureOpenAiClientException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(AzureOpenAiClientException.Reason.TIMEOUT)
                );
        assertThat(requests).hasValue(1);
    }

    @Test
    void deveLimitarCorpoDaResposta() throws Exception {
        iniciarServidor(exchange -> responder(exchange, 200, "x".repeat(2049)));

        assertThatThrownBy(() -> client(Duration.ofSeconds(2), 2048)
                .obterRespostaEstruturada(List.of(Map.of("role", "user", "content", "teste"))))
                .isInstanceOfSatisfying(
                        AzureOpenAiClientException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(AzureOpenAiClientException.Reason.RESPONSE_TOO_LARGE)
                );
    }

    private AzureOpenAiClient client(Duration timeout, int maxResponseBytes) {
        return new AzureOpenAiClient(
                HttpClient.newBuilder().connectTimeout(timeout).build(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1/chat/completions"),
                "deployment-teste",
                timeout,
                700,
                maxResponseBytes,
                new AzureOpenAiApiKeyAuthentication("chave-teste"),
                objectMapper
        );
    }

    private void iniciarServidor(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/openai/v1/chat/completions", exchange -> handler.handle(exchange));
        server.start();
    }

    private void responder(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String respostaValida() throws IOException {
        String content = objectMapper.writeValueAsString(Map.of(
                "tipoSolicitacao", "DUVIDA_ADMINISTRATIVA",
                "categoria", "ATENDIMENTO_ADMINISTRATIVO",
                "respostaGerada", "Resposta segura",
                "necessitaAtendimentoHumano", false,
                "motivoEncaminhamento", ""
        )).replace("\"motivoEncaminhamento\":\"\"", "\"motivoEncaminhamento\":null")
                .replace("}", ",\"confianca\":90}");
        return objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of(
                        "finish_reason", "stop",
                        "message", Map.of("content", content)
                ))
        ));
    }

    private static Stream<Arguments> errosHttp() {
        return Stream.of(
                Arguments.of(401, "{}", AzureOpenAiClientException.Reason.AUTHENTICATION),
                Arguments.of(403, "{}", AzureOpenAiClientException.Reason.AUTHORIZATION),
                Arguments.of(429, "{}", AzureOpenAiClientException.Reason.RATE_LIMIT),
                Arguments.of(503, "{}", AzureOpenAiClientException.Reason.PROVIDER_UNAVAILABLE),
                Arguments.of(400, "{\"error\":{\"code\":\"content_filter\"}}", AzureOpenAiClientException.Reason.CONTENT_FILTER)
        );
    }

    private static Stream<Arguments> respostasInterrompidas() {
        return Stream.of(
                Arguments.of(
                        "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"refusal\":\"recusado\",\"content\":null}}]}",
                        AzureOpenAiClientException.Reason.REFUSAL
                ),
                Arguments.of(
                        "{\"choices\":[{\"finish_reason\":\"content_filter\",\"message\":{\"content\":null}}]}",
                        AzureOpenAiClientException.Reason.CONTENT_FILTER
                ),
                Arguments.of(
                        "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}",
                        AzureOpenAiClientException.Reason.TRUNCATED_OUTPUT
                ),
                Arguments.of("{\"choices\":[]}", AzureOpenAiClientException.Reason.INVALID_OUTPUT)
        );
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
