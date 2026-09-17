package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.client.CepLookupClient;
import br.edu.usc.campusiachatbot.dto.CepLookupResponseDTO;
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
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest(properties = "gemini.api-key=")
@ActiveProfiles("test")
class ChatbotServiceTest {

    @Autowired
    private ChatbotService chatbotService;

    @Autowired
    private AtendimentoRepository atendimentoRepository;

    @MockitoBean
    private CepLookupClient cepLookupClient;

    @BeforeEach
    void limparBase() {
        atendimentoRepository.deleteAll();
        when(cepLookupClient.consultar(any())).thenReturn(Optional.empty());
        when(cepLookupClient.buscarPorEndereco(any(), any(), any())).thenReturn(List.of());
        when(cepLookupClient.consultar("17180959"))
                .thenReturn(Optional.of(new CepLookupResponseDTO("17180-959", "Iacanga", "SP", false)));
        when(cepLookupClient.consultar("17047001"))
                .thenReturn(Optional.of(new CepLookupResponseDTO("17047-001", "Bauru", "SP", false)));
        when(cepLookupClient.buscarPorEndereco("SP", "Iacanga", "Rua Bauru"))
                .thenReturn(List.of(new CepLookupResponseDTO(
                        "17180-959",
                        "Rua Bauru",
                        "",
                        "Centro",
                        "Iacanga",
                        "SP",
                        false
                )));
    }

    @Test
    void deveProcessarDuvidaAdministrativaSemAtendimentoHumano() {
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "14999999999",
                "Maria",
                "Gostaria de saber se voces fazem entrega.",
                OrigemMensagemEnum.WHATSAPP
        );

        ChatbotResponseDTO response = chatbotService.processarMensagem(request, OrigemMensagemEnum.SIMULADOR);

        assertThat(response.idAtendimento()).isNotNull();
        assertThat(response.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.ENTREGA);
        assertThat(response.necessitaAtendimentoHumano()).isFalse();
        assertThat(response.status()).isEqualTo(StatusAtendimentoEnum.PROCESSADO);
        assertThat(atendimentoRepository.count()).isEqualTo(1);
    }

    @Test
    void deveEncaminharDuvidaFarmaceuticaParaAtendimentoHumano() {
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "14999999999",
                "Maria",
                "Qual dose desse medicamento posso dar para uma crianca?",
                OrigemMensagemEnum.WHATSAPP
        );

        ChatbotResponseDTO response = chatbotService.processarMensagem(request, OrigemMensagemEnum.SIMULADOR);

        assertThat(response.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.DUVIDA_FARMACEUTICA);
        assertThat(response.necessitaAtendimentoHumano()).isTrue();
        assertThat(response.status()).isEqualTo(StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA);
        assertThat(response.motivoEncaminhamento()).contains("farmaceutica");
        assertThat(response.confianca()).isEqualTo(92.0);
    }

    @Test
    void deveResponderHorarioConfiguradoSemAtendimentoHumano() {
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "14999999999",
                "Maria",
                "Bom dia, qual o horario de funcionamento?",
                OrigemMensagemEnum.WHATSAPP
        );

        ChatbotResponseDTO response = chatbotService.processarMensagem(request, OrigemMensagemEnum.SIMULADOR);

        assertThat(response.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.HORARIO_FUNCIONAMENTO);
        assertThat(response.necessitaAtendimentoHumano()).isFalse();
        assertThat(response.status()).isEqualTo(StatusAtendimentoEnum.PROCESSADO);
        assertThat(response.respostaGerada()).contains("Segunda a sexta das 08:00 as 18:00");
        assertThat(response.confianca()).isEqualTo(100.0);
    }

    @Test
    void deveResponderPagamentoComMedicamentoSemAtendimentoHumano() {
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "14996181838",
                "Heron Meneses",
                "Se eu comprar um medicamento posso pagar em Pix?",
                OrigemMensagemEnum.WHATSAPP
        );

        ChatbotResponseDTO response = chatbotService.processarMensagem(request, OrigemMensagemEnum.SIMULADOR);

        assertThat(response.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.FORMAS_PAGAMENTO);
        assertThat(response.necessitaAtendimentoHumano()).isFalse();
        assertThat(response.status()).isEqualTo(StatusAtendimentoEnum.PROCESSADO);
        assertThat(response.respostaGerada()).contains("Pix");
        assertThat(response.confianca()).isEqualTo(100.0);
    }

    @Test
    void deveResponderEntregaNaCidadeQuandoClienteInformaEndereco() {
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "11970758547",
                "Danilo Genaro",
                "Voce faz entrega na rua Bauru 123?",
                OrigemMensagemEnum.WHATSAPP
        );

        ChatbotResponseDTO response = chatbotService.processarMensagem(request, OrigemMensagemEnum.SIMULADOR);

        assertThat(response.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.ENTREGA);
        assertThat(response.necessitaAtendimentoHumano()).isFalse();
        assertThat(response.status()).isEqualTo(StatusAtendimentoEnum.PROCESSADO);
        assertThat(response.respostaGerada()).contains("Iacanga");
        assertThat(response.confianca()).isEqualTo(100.0);
    }

    @Test
    void deveResponderEntregaEmIacangaQuandoClienteInformaCep() {
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "11970758547",
                "Danilo Genaro",
                "Voce faz entrega na rua Bauru 123 Iacanga cep : 17180-959?",
                OrigemMensagemEnum.WHATSAPP
        );

        ChatbotResponseDTO response = chatbotService.processarMensagem(request, OrigemMensagemEnum.SIMULADOR);

        assertThat(response.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.ENTREGA);
        assertThat(response.necessitaAtendimentoHumano()).isFalse();
        assertThat(response.status()).isEqualTo(StatusAtendimentoEnum.PROCESSADO);
        assertThat(response.respostaGerada()).isEqualTo("Entregamos em toda a cidade de Iacanga.");
        assertThat(response.confianca()).isEqualTo(100.0);
    }

    @Test
    void deveResponderEntregaQuandoClienteInformaEnderecoSemCep() {
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "11970758547",
                "Danilo Genaro",
                "Voce faz entrega na rua Bauru 123 Iacanga?",
                OrigemMensagemEnum.WHATSAPP
        );

        ChatbotResponseDTO response = chatbotService.processarMensagem(request, OrigemMensagemEnum.SIMULADOR);

        assertThat(response.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.ENTREGA);
        assertThat(response.necessitaAtendimentoHumano()).isFalse();
        assertThat(response.status()).isEqualTo(StatusAtendimentoEnum.PROCESSADO);
        assertThat(response.respostaGerada()).isEqualTo("Entregamos em toda a cidade de Iacanga.");
        assertThat(response.confianca()).isEqualTo(100.0);
    }

    @Test
    void deveResponderEntregaQuandoCepForAtendidoMesmoSemCidade() {
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "11970758547",
                "Danilo Genaro",
                "Voce faz entrega na rua Bauru 123 cep : 17180-959?",
                OrigemMensagemEnum.WHATSAPP
        );

        ChatbotResponseDTO response = chatbotService.processarMensagem(request, OrigemMensagemEnum.SIMULADOR);

        assertThat(response.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.ENTREGA);
        assertThat(response.necessitaAtendimentoHumano()).isFalse();
        assertThat(response.status()).isEqualTo(StatusAtendimentoEnum.PROCESSADO);
        assertThat(response.respostaGerada()).isEqualTo("Entregamos em toda a cidade de Iacanga.");
        assertThat(response.confianca()).isEqualTo(100.0);
    }

    @Test
    void deveResponderEnderecoConfiguradoSemAtendimentoHumano() {
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "14999999999",
                "Maria",
                "Qual o endereco de voces?",
                OrigemMensagemEnum.WHATSAPP
        );

        ChatbotResponseDTO response = chatbotService.processarMensagem(request, OrigemMensagemEnum.SIMULADOR);

        assertThat(response.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.DUVIDA_ADMINISTRATIVA);
        assertThat(response.necessitaAtendimentoHumano()).isFalse();
        assertThat(response.respostaGerada()).contains("Rua Teste, 123");
        assertThat(response.confianca()).isEqualTo(100.0);
    }

    @Test
    void deveResponderEntregaViaCorreioParaTodoBrasil() {
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "14999999999",
                "Maria",
                "Voces fazem entrega pelos correios?",
                OrigemMensagemEnum.WHATSAPP
        );

        ChatbotResponseDTO response = chatbotService.processarMensagem(request, OrigemMensagemEnum.SIMULADOR);

        assertThat(response.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.ENTREGA);
        assertThat(response.necessitaAtendimentoHumano()).isFalse();
        assertThat(response.respostaGerada()).containsIgnoringCase("todo o Brasil");
        assertThat(response.confianca()).isEqualTo(100.0);
    }

    @Test
    void deveResponderMotoboyInformandoCidadesAtendidas() {
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "14999999999",
                "Maria",
                "Voces tem motoboy?",
                OrigemMensagemEnum.WHATSAPP
        );

        ChatbotResponseDTO response = chatbotService.processarMensagem(request, OrigemMensagemEnum.SIMULADOR);

        assertThat(response.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.ENTREGA);
        assertThat(response.necessitaAtendimentoHumano()).isFalse();
        assertThat(response.respostaGerada()).contains("Motoboy");
        assertThat(response.respostaGerada()).contains("Iacanga");
        assertThat(response.confianca()).isEqualTo(100.0);
    }

    @Test
    void deveContinuarAtendimentoNaMesmaJanelaDeSessao() {
        ChatbotRequestDTO primeira = new ChatbotRequestDTO(
                "14999999999",
                "Maria",
                "Qual o horario de funcionamento?",
                OrigemMensagemEnum.WHATSAPP
        );
        ChatbotRequestDTO segunda = new ChatbotRequestDTO(
                "14999999999",
                "Maria",
                "E o endereco de voces?",
                OrigemMensagemEnum.WHATSAPP
        );

        chatbotService.processarMensagem(primeira, OrigemMensagemEnum.SIMULADOR);
        ChatbotResponseDTO respostaSegunda = chatbotService.processarMensagem(segunda, OrigemMensagemEnum.SIMULADOR);

        assertThat(atendimentoRepository.count()).isEqualTo(1);
        assertThat(respostaSegunda.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.DUVIDA_ADMINISTRATIVA);
        assertThat(respostaSegunda.respostaGerada()).contains("Rua Teste, 123");
    }

    @Test
    void deveInformarCoberturaApenasEmIacangaQuandoClienteInformaBauru() {
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "11970758547",
                "Danilo Genaro",
                "Voce faz entrega na avenida Orlando Ranieri, 7-108 Jardim Maramba Bauru cep 17047001",
                OrigemMensagemEnum.WHATSAPP
        );

        ChatbotResponseDTO response = chatbotService.processarMensagem(request, OrigemMensagemEnum.SIMULADOR);

        assertThat(response.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.ENTREGA);
        assertThat(response.necessitaAtendimentoHumano()).isFalse();
        assertThat(response.status()).isEqualTo(StatusAtendimentoEnum.PROCESSADO);
        assertThat(response.respostaGerada()).isEqualTo("No momento, fazemos entrega apenas em: Iacanga.");
        assertThat(response.confianca()).isEqualTo(100.0);
    }
}
