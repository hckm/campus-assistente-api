package br.edu.usc.campusiachatbot.domain;

public record InicioInteracao(
        Atendimento atendimento,
        String processamentoToken,
        String eventoId,
        boolean repetida
) {
}
