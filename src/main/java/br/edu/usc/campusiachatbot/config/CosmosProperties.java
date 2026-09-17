package br.edu.usc.campusiachatbot.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.URISyntaxException;

@Getter
@Setter
@Validated
public class CosmosProperties {

    @NotBlank
    private String endpoint;

    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "[A-Za-z0-9._-]+")
    private String database = "campus-ia-chatbot";

    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "[A-Za-z0-9._-]+")
    private String conversasContainer = "conversas";

    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "[A-Za-z0-9._-]+")
    private String catalogoContainer = "catalogo";

    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "[A-Za-z0-9._-]+")
    private String estabelecimentosContainer = "estabelecimentos";

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "[A-Za-z0-9._-]+")
    private String catalogoId = "renovo";

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "[A-Za-z0-9._-]+")
    private String estabelecimentoId = "renovo";

    @Min(1)
    @Max(100)
    private int catalogoBatchMaxOperations = 100;

    @Min(1)
    @Max(1900000)
    private int catalogoBatchMaxBytes = 1900000;

    @NotNull
    private Autenticacao autenticacao = Autenticacao.MANAGED_IDENTITY;

    private String managedIdentityClientId;
    private String emulatorKey;

    @AssertTrue(message = "endpoint deve usar HTTPS, host e porta validos, sem credenciais, caminho, consulta ou fragmento")
    public boolean isEndpointValido() {
        if (endpoint == null || endpoint.isBlank()) {
            return true;
        }
        try {
            URI uri = endpointUri();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && (uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && (uri.getPort() == -1 || (uri.getPort() > 0 && uri.getPort() <= 65535));
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    public URI endpointUri() {
        try {
            return new URI(endpoint);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("endpoint Cosmos invalido");
        }
    }

    @AssertTrue(message = "containers de conversas, catalogo e estabelecimentos devem ser distintos")
    public boolean isContainersDistintos() {
        return conversasContainer == null || catalogoContainer == null || estabelecimentosContainer == null
                || (!conversasContainer.equals(catalogoContainer)
                && !conversasContainer.equals(estabelecimentosContainer)
                && !catalogoContainer.equals(estabelecimentosContainer));
    }

    public enum Autenticacao {
        MANAGED_IDENTITY,
        EMULATOR
    }
}
