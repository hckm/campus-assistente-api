package br.edu.usc.campusiachatbot.controller;

import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.enums.OrigemMensagemEnum;
import br.edu.usc.campusiachatbot.repository.AtendimentoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "gemini.api-key=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WhatsAppWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AtendimentoRepository atendimentoRepository;

    @BeforeEach
    void setUp() {
        atendimentoRepository.deleteAll();
    }

    @Test
    void deveProcessarMensagemViaWebhookWhatsApp() throws Exception {
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "14999999999",
                "Carlos",
                "Qual o horario de funcionamento?",
                OrigemMensagemEnum.WHATSAPP
        );

        mockMvc.perform(post("/api/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAtendimento").isString())
                .andExpect(jsonPath("$.tipoSolicitacao").value("HORARIO_FUNCIONAMENTO"))
                .andExpect(jsonPath("$.status").value("PROCESSADO"));
    }

    @Test
    void deveRetornar400QuandoTelefoneNaoInformado() throws Exception {
        String payload = """
                {"telefoneCliente":"","mensagem":"ola","nomeCliente":"X"}
                """;

        mockMvc.perform(post("/api/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDATION_ERROR"));
    }

    @Test
    void deveRetornar400QuandoMensagemNaoInformada() throws Exception {
        String payload = """
                {"telefoneCliente":"14999999999","mensagem":"","nomeCliente":"X"}
                """;

        mockMvc.perform(post("/api/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDATION_ERROR"));
    }
}
