package br.edu.usc.campusiachatbot.client;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AzureOpenAiAuthenticationTest {

    @Test
    void deveAplicarChaveNoCabecalhoDaApi() {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://example.openai.azure.com"));

        new AzureOpenAiApiKeyAuthentication("segredo-teste").apply(builder);

        assertThat(builder.build().headers().firstValue("api-key")).contains("segredo-teste");
    }

    @Test
    void deveSolicitarTokenFoundryEAplicarBearer() {
        AtomicReference<List<String>> scopes = new AtomicReference<>();
        AzureOpenAiManagedIdentityAuthentication authentication = new AzureOpenAiManagedIdentityAuthentication(
                context -> token(context, scopes),
                Duration.ofSeconds(1)
        );
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://example.openai.azure.com"));

        authentication.apply(builder);

        assertThat(scopes.get()).containsExactly("https://ai.azure.com/.default");
        assertThat(builder.build().headers().firstValue("Authorization")).contains("Bearer token-teste");
    }

    private Mono<AccessToken> token(TokenRequestContext context, AtomicReference<List<String>> scopes) {
        scopes.set(context.getScopes());
        return Mono.just(new AccessToken("token-teste", OffsetDateTime.now().plusHours(1)));
    }
}
