package br.edu.usc.campusiachatbot.exception;

public class InteracaoEmAndamentoException extends RuntimeException {

    public InteracaoEmAndamentoException() {
        super("Ja existe uma interacao em processamento para este cliente; tente novamente mais tarde");
    }
}
