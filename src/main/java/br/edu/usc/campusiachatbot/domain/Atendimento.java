package br.edu.usc.campusiachatbot.domain;

import br.edu.usc.campusiachatbot.enums.CategoriaAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.OrigemMensagemEnum;
import br.edu.usc.campusiachatbot.enums.StatusAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.TipoSolicitacaoEnum;

import java.time.LocalDateTime;

public record Atendimento(
        String id,
        String clienteChave,
        String telefoneCliente,
        String nomeCliente,
        OrigemMensagemEnum origem,
        String mensagemCliente,
        TipoSolicitacaoEnum tipoSolicitacao,
        CategoriaAtendimentoEnum categoria,
        String respostaGerada,
        boolean necessitaAtendimentoHumano,
        String motivoEncaminhamento,
        Double confianca,
        StatusAtendimentoEnum status,
        LocalDateTime dataProcessamento
) {
}
