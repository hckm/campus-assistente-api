package br.edu.usc.campusiachatbot.exception;

public class InteracaoFalhouException extends RuntimeException {

    public InteracaoFalhouException() {
        super("A interacao idempotente anterior falhou e nao sera reexecutada automaticamente");
    }
}
