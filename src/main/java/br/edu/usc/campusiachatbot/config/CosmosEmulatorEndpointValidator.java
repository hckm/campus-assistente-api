package br.edu.usc.campusiachatbot.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

public final class CosmosEmulatorEndpointValidator {

    private static final Set<String> HOSTS_PERMITIDOS = Set.of("localhost", "127.0.0.1", "[::1]");

    private CosmosEmulatorEndpointValidator() {
    }

    public static URI validar(String endpoint, String key) {
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException | NullPointerException exception) {
            throw new IllegalStateException("Autenticacao EMULATOR exige endpoint HTTPS de loopback local", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !HOSTS_PERMITIDOS.contains(uri.getHost())
                || uri.getUserInfo() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                || uri.getQuery() != null
                || uri.getFragment() != null
                || uri.getPort() < 1
                || uri.getPort() > 65535) {
            throw new IllegalStateException("Autenticacao EMULATOR exige endpoint HTTPS de loopback local");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Autenticacao EMULATOR exige emulator-key externa");
        }
        return uri;
    }
}
