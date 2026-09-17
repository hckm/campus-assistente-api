package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.dto.InterpretacaoIaResponseDTO;
import br.edu.usc.campusiachatbot.enums.CategoriaAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.TipoSolicitacaoEnum;

public record RespostaInterpretacaoIaInterna(
        TipoSolicitacaoEnum tipoSolicitacao,
        CategoriaAtendimentoEnum categoria,
        String respostaGerada,
        boolean necessitaAtendimentoHumano,
        String motivoEncaminhamento,
        double confianca,
        boolean solicitarCategorias,
        ConsultaCatalogoIa consultaCatalogo
) {

    public InterpretacaoIaResponseDTO respostaPublica() {
        return new InterpretacaoIaResponseDTO(
                tipoSolicitacao,
                categoria,
                respostaGerada,
                necessitaAtendimentoHumano,
                motivoEncaminhamento,
                confianca
        ).normalizado();
    }
}
