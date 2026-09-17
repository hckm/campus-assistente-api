package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.client.CepLookupClient;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.entity.AtendimentoEntity;
import br.edu.usc.campusiachatbot.enums.OrigemMensagemEnum;
import br.edu.usc.campusiachatbot.enums.StatusAtendimentoEnum;
import br.edu.usc.campusiachatbot.repository.AtendimentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "gemini.api-key=")
@ActiveProfiles("test")
class ChatbotServiceExceptionTest {

    @Autowired
    private ChatbotService chatbotService;

    @Autowired
    private AtendimentoRepository atendimentoRepository;

    @MockitoBean
    private InterpretacaoIaService interpretacaoIaService;

    @MockitoBean
    private CepLookupClient cepLookupClient;

    @BeforeEach
    void setUp() {
        atendimentoRepository.deleteAll();
        when(cepLookupClient.consultar(any())).thenReturn(Optional.empty());
        when(cepLookupClient.buscarPorEndereco(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void deveMarcarAtendimentoComoErroQuandoInterpretacaoIaLancaExcecao() {
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "14999999999",
                "Maria",
                "ola",
                OrigemMensagemEnum.WHATSAPP
        );
        when(interpretacaoIaService.interpretarMensagem(any(), any(), any()))
                .thenThrow(new RuntimeException("falha simulada no gemini"));

        assertThatThrownBy(() -> chatbotService.processarMensagem(request, OrigemMensagemEnum.SIMULADOR))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("falha simulada no gemini");

        List<AtendimentoEntity> atendimentos = atendimentoRepository.findAll();
        assertThat(atendimentos).hasSize(1);
        assertThat(atendimentos.get(0).getStatus()).isEqualTo(StatusAtendimentoEnum.ERRO_PROCESSAMENTO);
    }
}
