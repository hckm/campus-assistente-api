package br.edu.usc.campusiachatbot.dto;

import br.edu.usc.campusiachatbot.enums.CategoriaAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.OrigemMensagemEnum;
import br.edu.usc.campusiachatbot.enums.StatusAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.TipoSolicitacaoEnum;

import java.time.LocalDateTime;

public record ChatbotResponseDTO(
        String idAtendimento,
        OrigemMensagemEnum origem,
        String mensagemCliente,
        TipoSolicitacaoEnum tipoSolicitacao,
        CategoriaAtendimentoEnum categoria,
        String respostaGerada,
        Boolean necessitaAtendimentoHumano,
        String motivoEncaminhamento,
        Double confianca,
        StatusAtendimentoEnum status,
        LocalDateTime dataProcessamento
) {
}
