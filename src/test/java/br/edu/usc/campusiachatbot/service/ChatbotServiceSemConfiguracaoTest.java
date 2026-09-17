package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.client.CepLookupClient;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.ChatbotResponseDTO;
import br.edu.usc.campusiachatbot.enums.OrigemMensagemEnum;
import br.edu.usc.campusiachatbot.enums.StatusAtendimentoEnum;
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
        "estabelecimento.horario-funcionamento=",
        "estabelecimento.formas-pagamento=",
        "estabelecimento.endereco=",
        "estabelecimento.cidades-atendidas=",
        "estabelecimento.entrega=Entregamos apenas nas cidades atendidas configuradas."
})
@ActiveProfiles("test")
class ChatbotServiceSemConfiguracaoTest {

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
    void deveEncaminharParaHumanoQuandoHorarioNaoConfigurado() {
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "14999999999",
                "Maria",
                "Qual o horario de funcionamento?",
                OrigemMensagemEnum.WHATSAPP
        );

        ChatbotResponseDTO response = chatbotService.processarMensagem(request, OrigemMensagemEnum.SIMULADOR);

        assertThat(response.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.HORARIO_FUNCIONAMENTO);
        assertThat(response.necessitaAtendimentoHumano()).isTrue();
        assertThat(response.status()).isEqualTo(StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA);
        assertThat(response.respostaGerada()).containsIgnoringCase("horario");
    }

    @Test
    void deveEncaminharParaHumanoQuandoPagamentoNaoConfigurado() {
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "14999999999",
                "Maria",
                "Voces aceitam pagamento por pix?",
                OrigemMensagemEnum.WHATSAPP
        );

        ChatbotResponseDTO response = chatbotService.processarMensagem(request, OrigemMensagemEnum.SIMULADOR);

        assertThat(response.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.FORMAS_PAGAMENTO);
        assertThat(response.necessitaAtendimentoHumano()).isTrue();
        assertThat(response.status()).isEqualTo(StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA);
    }

    @Test
    void deveEncaminharParaHumanoQuandoEnderecoNaoConfigurado() {
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "14999999999",
                "Maria",
                "Qual o endereco de voces?",
                OrigemMensagemEnum.WHATSAPP
        );

        ChatbotResponseDTO response = chatbotService.processarMensagem(request, OrigemMensagemEnum.SIMULADOR);

        assertThat(response.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.DUVIDA_ADMINISTRATIVA);
        assertThat(response.necessitaAtendimentoHumano()).isTrue();
        assertThat(response.status()).isEqualTo(StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA);
    }

    @Test
    void deveResponderEntregaGenericaQuandoNaoHaCidadesNemTextoCustomizado() {
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "14999999999",
                "Maria",
                "Voces fazem entrega?",
                OrigemMensagemEnum.WHATSAPP
        );

        ChatbotResponseDTO response = chatbotService.processarMensagem(request, OrigemMensagemEnum.SIMULADOR);

        assertThat(response.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.ENTREGA);
        assertThat(response.necessitaAtendimentoHumano()).isFalse();
        assertThat(response.respostaGerada()).containsIgnoringCase("CEP");
    }
}
