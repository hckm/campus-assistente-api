package br.edu.usc.campusiachatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PortalChatRequestDTO(
        @NotBlank(message = "sessionId e obrigatorio")
        @Pattern(regexp = "[0-9]{8,20}", message = "sessionId deve conter entre 8 e 20 digitos")
        String sessionId,

        @NotBlank(message = "message e obrigatoria")
        @Size(max = 4000, message = "message deve ter no maximo 4000 caracteres")
        String message
) {
    public ChatbotRequestDTO toChatbotRequest() {
        return new ChatbotRequestDTO(sessionId, null, message, null);
    }
}
