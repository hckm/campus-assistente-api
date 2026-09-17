package br.edu.usc.campusiachatbot.client;

import java.net.http.HttpRequest;

public class AzureOpenAiApiKeyAuthentication implements AzureOpenAiAuthentication {

    private final String apiKey;

    public AzureOpenAiApiKeyAuthentication(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void apply(HttpRequest.Builder requestBuilder) {
        requestBuilder.header("api-key", apiKey);
    }
}
