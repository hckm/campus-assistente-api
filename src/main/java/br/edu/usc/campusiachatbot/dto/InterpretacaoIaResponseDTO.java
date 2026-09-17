package br.edu.usc.campusiachatbot.dto;

import br.edu.usc.campusiachatbot.enums.CategoriaAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.TipoSolicitacaoEnum;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InterpretacaoIaResponseDTO(
        TipoSolicitacaoEnum tipoSolicitacao,
        CategoriaAtendimentoEnum categoria,
        String respostaGerada,
        Boolean necessitaAtendimentoHumano,
        String motivoEncaminhamento,
        Double confianca
) {
    public InterpretacaoIaResponseDTO normalizado() {
        return new InterpretacaoIaResponseDTO(
                tipoSolicitacao == null ? TipoSolicitacaoEnum.OUTROS : tipoSolicitacao,
                categoria == null ? CategoriaAtendimentoEnum.OUTROS : categoria,
                respostaGerada == null || respostaGerada.isBlank()
                        ? "Recebemos sua mensagem e vamos dar continuidade ao atendimento."
                        : respostaGerada,
                necessitaAtendimentoHumano != null && necessitaAtendimentoHumano,
                motivoEncaminhamento,
                normalizarConfianca(confianca)
        );
    }

    private Double normalizarConfianca(Double valor) {
        if (valor == null) {
            return 0.0;
        }

        double percentual = valor <= 1.0 ? valor * 100.0 : valor;
        return Math.max(0.0, Math.min(100.0, percentual));
    }
}
