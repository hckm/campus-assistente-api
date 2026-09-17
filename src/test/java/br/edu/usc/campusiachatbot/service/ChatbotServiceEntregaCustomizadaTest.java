package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.client.CepLookupClient;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.ChatbotResponseDTO;
import br.edu.usc.campusiachatbot.enums.OrigemMensagemEnum;
import br.edu.usc.campusiachatbot.enums.TipoSolicitacaoEnum;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "gemini.api-key=",
        "estabelecimento.cidades-atendidas=",
        "estabelecimento.entrega=Entregamos para todo o Brasil pelo Correio."
})
@ActiveProfiles("test")
class ChatbotServiceEntregaCustomizadaTest {

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
    void deveResponderEntregaComTextoCustomizadoQuandoNaoHaCidades() {
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "14999999999",
                "Maria",
                "Voces fazem entrega?",
                OrigemMensagemEnum.WHATSAPP
        );

        ChatbotResponseDTO response = chatbotService.processarMensagem(request, OrigemMensagemEnum.SIMULADOR);

        assertThat(response.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.ENTREGA);
        assertThat(response.necessitaAtendimentoHumano()).isFalse();
        assertThat(response.respostaGerada()).isEqualTo("Entregamos para todo o Brasil pelo Correio.");
        assertThat(response.confianca()).isEqualTo(100.0);
    }
}
