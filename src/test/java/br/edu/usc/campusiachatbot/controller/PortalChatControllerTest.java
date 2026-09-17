package br.edu.usc.campusiachatbot.controller;

import br.edu.usc.campusiachatbot.dto.PortalChatRequestDTO;
import br.edu.usc.campusiachatbot.repository.AtendimentoRepository;
import br.edu.usc.campusiachatbot.repository.MensagemAtendimentoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "gemini.api-key=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortalChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AtendimentoRepository atendimentoRepository;

    @Autowired
    private MensagemAtendimentoRepository mensagemAtendimentoRepository;

    @BeforeEach
    void limparBase() {
        mensagemAtendimentoRepository.deleteAll();
        atendimentoRepository.deleteAll();
    }

    @Test
    void deveProcessarMensagemDoPortal() throws Exception {
        PortalChatRequestDTO request = new PortalChatRequestDTO(
                "17261424001234567890",
                "Gostaria de saber se voces fazem entrega."
        );

        mockMvc.perform(post("/api/portal/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAtendimento").isString())
                .andExpect(jsonPath("$.origem").value("PORTAL"))
                .andExpect(jsonPath("$.tipoSolicitacao").value("ENTREGA"))
                .andExpect(jsonPath("$.respostaGerada").isString())
                .andExpect(jsonPath("$.status").value("PROCESSADO"));
    }

    @Test
    void deveValidarContratoDoPortal() throws Exception {
        mockMvc.perform(post("/api/portal/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"abc\",\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDATION_ERROR"));
    }

    @Test
    void deveManterSessaoEncaminhadaNaMesmaConversaDoPortal() throws Exception {
        PortalChatRequestDTO encaminhamento = new PortalChatRequestDTO(
                "17261424001234567890",
                "Qual dose desse medicamento posso dar para uma crianca?"
        );

        String atendimentoId = mockMvc.perform(post("/api/portal/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encaminhamento)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_ANALISE_HUMANA"))
                .andExpect(jsonPath("$.necessitaAtendimentoHumano").value(true))
                .andReturn().getResponse().getContentAsString();
        String idOriginal = objectMapper.readTree(atendimentoId).path("idAtendimento").asText();
        String motivoOriginal = objectMapper.readTree(atendimentoId).path("motivoEncaminhamento").asText();

        PortalChatRequestDTO perguntaAdministrativa = new PortalChatRequestDTO(
                "17261424001234567890",
                "Qual e o horario de funcionamento?"
        );

        mockMvc.perform(post("/api/portal/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(perguntaAdministrativa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAtendimento").value(idOriginal))
                .andExpect(jsonPath("$.status").value("AGUARDANDO_ANALISE_HUMANA"))
                .andExpect(jsonPath("$.necessitaAtendimentoHumano").value(true))
                .andExpect(jsonPath("$.motivoEncaminhamento").value(motivoOriginal));

        assertThat(atendimentoRepository.count()).isEqualTo(1);
        assertThat(mensagemAtendimentoRepository.count()).isEqualTo(4);
    }

    @Test
    void devePermitirCorsParaPortalLocal() throws Exception {
        mockMvc.perform(options("/api/portal/chat")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5500")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5500"));
    }
}
