package br.edu.usc.campusiachatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@ConfigurationProperties(prefix = "azure.openai")
public record AzureOpenAiProperties(
        String endpoint,
        String deployment,
        Authentication authentication,
        String apiKey,
        String managedIdentityClientId,
        Duration timeout,
        Integer maxOutputTokens,
        Integer maxResponseBytes
) {

    public AzureOpenAiProperties {
        endpoint = normalizar(endpoint);
        deployment = normalizar(deployment);
        authentication = authentication == null ? Authentication.MANAGED_IDENTITY : authentication;
        apiKey = normalizar(apiKey);
        managedIdentityClientId = normalizar(managedIdentityClientId);
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        maxOutputTokens = maxOutputTokens == null ? 800 : maxOutputTokens;
        maxResponseBytes = maxResponseBytes == null ? 65536 : maxResponseBytes;
    }

    public void validarSelecionado() {
        URI uri = endpointUri();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : removerBarraFinal(uri.getPath());

        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !host.endsWith(".openai.azure.com")
                || uri.getUserInfo() != null
                || uri.getPort() != -1
                || uri.getQuery() != null
                || uri.getFragment() != null
                || !(path.isEmpty() || "/openai/v1".equals(path))) {
            throw new IllegalStateException("azure.openai.endpoint deve ser um endpoint de inferencia Azure OpenAI HTTPS");
        }
        if (deployment.isBlank() || deployment.length() > 128 || !deployment.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalStateException("azure.openai.deployment deve identificar um deployment valido");
        }
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalStateException("azure.openai.timeout deve ser positivo e no maximo cinco minutos");
        }
        if (maxOutputTokens < 1 || maxOutputTokens > 100000) {
            throw new IllegalStateException("azure.openai.max-output-tokens deve estar entre 1 e 100000");
        }
        if (maxResponseBytes < 1024 || maxResponseBytes > 1048576) {
            throw new IllegalStateException("azure.openai.max-response-bytes deve estar entre 1024 e 1048576");
        }
        if (authentication == Authentication.API_KEY && apiKey.isBlank()) {
            throw new IllegalStateException("azure.openai.api-key e obrigatoria para autenticacao API_KEY");
        }
        if (authentication == Authentication.API_KEY && (apiKey.contains("\r") || apiKey.contains("\n"))) {
            throw new IllegalStateException("azure.openai.api-key contem caracteres invalidos");
        }
        if (authentication == Authentication.MANAGED_IDENTITY && !managedIdentityClientId.isBlank()) {
            validarClientId();
        }
    }

    public URI chatCompletionsUri() {
        URI uri = endpointUri();
        String path = removerBarraFinal(uri.getPath() == null ? "" : uri.getPath());
        String sufixo = path.isEmpty() ? "/openai/v1/chat/completions" : "/chat/completions";
        return uri.resolve(path + sufixo);
    }

    public boolean hasManagedIdentityClientId() {
        return !managedIdentityClientId.isBlank();
    }

    private URI endpointUri() {
        if (endpoint.isBlank()) {
            throw new IllegalStateException("azure.openai.endpoint e obrigatorio quando AZURE_OPENAI esta selecionado");
        }
        try {
            return new URI(endpoint);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("azure.openai.endpoint deve ser uma URI valida");
        }
    }

    private void validarClientId() {
        try {
            UUID clientId = UUID.fromString(managedIdentityClientId);
            if (!clientId.toString().equalsIgnoreCase(managedIdentityClientId)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("azure.openai.managed-identity-client-id deve ser um UUID valido");
        }
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static String removerBarraFinal(String valor) {
        return valor.endsWith("/") ? valor.substring(0, valor.length() - 1) : valor;
    }

    public enum Authentication {
        MANAGED_IDENTITY,
        API_KEY
    }
}
