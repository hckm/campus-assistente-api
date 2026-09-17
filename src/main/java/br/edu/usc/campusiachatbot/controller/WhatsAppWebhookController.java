package br.edu.usc.campusiachatbot.controller;

import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.ChatbotResponseDTO;
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
@RequestMapping("/api/webhook/whatsapp")
@Tag(name = "WhatsApp Webhook", description = "Endpoint preparado para entrada de mensagens via provedor WhatsApp")
@RequiredArgsConstructor
public class WhatsAppWebhookController {

    private final ChatbotService chatbotService;

    @PostMapping
    @Operation(summary = "Recebe uma mensagem enviada pelo canal WhatsApp")
    public ResponseEntity<ChatbotResponseDTO> receberMensagem(
            @Valid @RequestBody ChatbotRequestDTO request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return ResponseEntity.ok(chatbotService.processarMensagem(
                request, OrigemMensagemEnum.WHATSAPP, idempotencyKey
        ));
    }
}
