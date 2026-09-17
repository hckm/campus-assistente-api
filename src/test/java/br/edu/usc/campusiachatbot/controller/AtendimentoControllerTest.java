package br.edu.usc.campusiachatbot.controller;

import br.edu.usc.campusiachatbot.client.CepLookupClient;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.ChatbotResponseDTO;
import br.edu.usc.campusiachatbot.enums.OrigemMensagemEnum;
import br.edu.usc.campusiachatbot.repository.AtendimentoRepository;
import br.edu.usc.campusiachatbot.service.ChatbotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "gemini.api-key=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AtendimentoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatbotService chatbotService;

    @Autowired
    private AtendimentoRepository atendimentoRepository;

    @MockitoBean
    private CepLookupClient cepLookupClient;

    @BeforeEach
    void setUp() {
        atendimentoRepository.deleteAll();
        when(cepLookupClient.consultar(any())).thenReturn(Optional.empty());
        when(cepLookupClient.buscarPorEndereco(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void deveListarTodosAtendimentos() throws Exception {
        criarAtendimento("11111111111", "Ana", "qual o horario?");
        criarAtendimento("22222222222", "Bruno", "qual o endereco?");

        mockMvc.perform(get("/api/atendimentos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isArray())
                .andExpect(jsonPath("$.itens.length()").value(2))
                .andExpect(jsonPath("$.proximoToken").doesNotExist());
    }

    @Test
    void deveBuscarAtendimentoPorId() throws Exception {
        ChatbotResponseDTO criado = criarAtendimento("11111111111", "Ana", "qual o horario?");

        mockMvc.perform(get("/api/atendimentos/{id}", criado.idAtendimento()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAtendimento").value(criado.idAtendimento()))
                .andExpect(jsonPath("$.tipoSolicitacao").value("HORARIO_FUNCIONAMENTO"));
    }

    @Test
    void deveRetornar404QuandoAtendimentoNaoEncontrado() throws Exception {
        mockMvc.perform(get("/api/atendimentos/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveListarAtendimentosPorStatus() throws Exception {
        criarAtendimento("11111111111", "Ana", "qual o horario?");

        mockMvc.perform(get("/api/atendimentos/status/PROCESSADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isArray())
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.proximoToken").doesNotExist());
    }

    @Test
    void deveRetornar400ParaStatusInvalido() throws Exception {
        mockMvc.perform(get("/api/atendimentos/status/INVALIDO"))
                .andExpect(status().isBadRequest());
    }

    private ChatbotResponseDTO criarAtendimento(String telefone, String nome, String mensagem) {
        return chatbotService.processarMensagem(
                new ChatbotRequestDTO(telefone, nome, mensagem, OrigemMensagemEnum.WHATSAPP),
                OrigemMensagemEnum.SIMULADOR
        );
    }
}
