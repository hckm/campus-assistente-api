package br.edu.usc.campusiachatbot.controller;

import br.edu.usc.campusiachatbot.dto.ChatbotResponseDTO;
import br.edu.usc.campusiachatbot.dto.PortalChatRequestDTO;
import br.edu.usc.campusiachatbot.enums.OrigemMensagemEnum;
import br.edu.usc.campusiachatbot.service.ChatbotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portal/chat")
@Tag(name = "Portal Chat", description = "Endpoint do assistente virtual exibido no portal")
@RequiredArgsConstructor
public class PortalChatController {

    private final ChatbotService chatbotService;

    @PostMapping
    @Operation(summary = "Processa uma mensagem enviada pelo chat do portal")
    public ResponseEntity<ChatbotResponseDTO> enviarMensagem(
            @Valid @RequestBody PortalChatRequestDTO request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return ResponseEntity.ok(chatbotService.processarMensagem(
                request.toChatbotRequest(), OrigemMensagemEnum.PORTAL, idempotencyKey
        ));
    }
}
