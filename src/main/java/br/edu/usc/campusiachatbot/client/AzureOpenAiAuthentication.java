package br.edu.usc.campusiachatbot.client;

import java.net.http.HttpRequest;

@FunctionalInterface
public interface AzureOpenAiAuthentication {

    void apply(HttpRequest.Builder requestBuilder);
}
