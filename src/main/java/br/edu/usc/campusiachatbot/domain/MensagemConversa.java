package br.edu.usc.campusiachatbot.domain;

import br.edu.usc.campusiachatbot.enums.DirecaoMensagemEnum;

public record MensagemConversa(DirecaoMensagemEnum direcao, String conteudo) {
}
