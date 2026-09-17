package br.edu.usc.campusiachatbot.service;

public class CodexCliException extends RuntimeException {

    private final Reason reason;

    public CodexCliException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public CodexCliException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        START_FAILURE,
        PROCESS_FAILURE,
        TIMEOUT,
        OUTPUT_LIMIT,
        INVALID_OUTPUT,
        INTERRUPTED
    }
}
