package br.edu.usc.campusiachatbot.dto;

import br.edu.usc.campusiachatbot.enums.OrigemMensagemEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatbotRequestDTO(
        @NotBlank(message = "telefoneCliente e obrigatorio")
        @Size(min = 8, max = 20, message = "telefoneCliente deve ter entre 8 e 20 caracteres")
        String telefoneCliente,

        @Size(max = 120, message = "nomeCliente deve ter no maximo 120 caracteres")
        String nomeCliente,

        @NotBlank(message = "mensagem e obrigatoria")
        @Size(max = 4000, message = "mensagem deve ter no maximo 4000 caracteres")
        String mensagem,

        OrigemMensagemEnum origem
) {
    public OrigemMensagemEnum origemOuDefault(OrigemMensagemEnum origemPadrao) {
        return origem == null ? origemPadrao : origem;
    }
}
