package br.edu.usc.campusiachatbot.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClienteChaveTest {

    @Test
    void deveUnificarSomenteFormatacaoDoMesmoNumero() {
        assertThat(ClienteChave.criar("14999999999"))
                .isEqualTo(ClienteChave.criar("(14) 99999-9999"));
    }

    @Test
    void devePreservarDiferencaDoCodigoDePais() {
        assertThat(ClienteChave.criar("+5514999999999"))
                .isNotEqualTo(ClienteChave.criar("14999999999"));
    }

    @Test
    void deveRejeitarCaracteresNaoReconhecidos() {
        assertThatThrownBy(() -> ClienteChave.criar("14 ABC 9999"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
