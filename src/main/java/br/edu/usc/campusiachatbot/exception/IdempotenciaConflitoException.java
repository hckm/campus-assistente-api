package br.edu.usc.campusiachatbot.exception;

public class IdempotenciaConflitoException extends RuntimeException {

    public IdempotenciaConflitoException() {
        super("Idempotency-Key ja utilizada com outro payload");
    }
}
