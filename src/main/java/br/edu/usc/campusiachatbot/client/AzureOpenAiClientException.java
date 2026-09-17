package br.edu.usc.campusiachatbot.client;

public class AzureOpenAiClientException extends RuntimeException {

    private final Reason reason;

    public AzureOpenAiClientException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AzureOpenAiClientException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        AUTHENTICATION,
        AUTHORIZATION,
        RATE_LIMIT,
        PROVIDER_UNAVAILABLE,
        PROVIDER_FAILURE,
        CONTENT_FILTER,
        REFUSAL,
        TRUNCATED_OUTPUT,
        INVALID_OUTPUT,
        RESPONSE_TOO_LARGE,
        TIMEOUT,
        INTERRUPTED
    }
}
