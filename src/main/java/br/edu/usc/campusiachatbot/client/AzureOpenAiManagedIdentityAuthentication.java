package br.edu.usc.campusiachatbot.client;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;

import java.net.http.HttpRequest;
import java.time.Duration;

public class AzureOpenAiManagedIdentityAuthentication implements AzureOpenAiAuthentication {

    private static final String SCOPE = "https://ai.azure.com/.default";

    private final TokenCredential credential;
    private final Duration timeout;

    public AzureOpenAiManagedIdentityAuthentication(TokenCredential credential, Duration timeout) {
        this.credential = credential;
        this.timeout = timeout;
    }

    @Override
    public void apply(HttpRequest.Builder requestBuilder) {
        try {
            AccessToken token = credential.getToken(new TokenRequestContext().addScopes(SCOPE)).block(timeout);
            if (token == null || token.getToken() == null || token.getToken().isBlank()) {
                throw new AzureOpenAiClientException(
                        AzureOpenAiClientException.Reason.AUTHENTICATION,
                        "A identidade gerenciada nao forneceu token para Azure OpenAI"
                );
            }
            requestBuilder.header("Authorization", "Bearer " + token.getToken());
        } catch (AzureOpenAiClientException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AzureOpenAiClientException(
                    AzureOpenAiClientException.Reason.AUTHENTICATION,
                    "Falha ao obter token da identidade gerenciada para Azure OpenAI",
                    exception
            );
        }
    }
}
