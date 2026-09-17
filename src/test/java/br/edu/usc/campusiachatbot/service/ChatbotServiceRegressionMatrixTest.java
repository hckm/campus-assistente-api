package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.client.CepLookupClient;
import br.edu.usc.campusiachatbot.config.ChatbotMessagesProperties;
import br.edu.usc.campusiachatbot.config.ChatbotSessionProperties;
import br.edu.usc.campusiachatbot.config.EstabelecimentoProperties;
import br.edu.usc.campusiachatbot.domain.Atendimento;
import br.edu.usc.campusiachatbot.domain.MensagemConversa;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.EnderecoEnriquecidoDTO;
import br.edu.usc.campusiachatbot.dto.InterpretacaoIaResponseDTO;
import br.edu.usc.campusiachatbot.enums.CategoriaAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.DirecaoMensagemEnum;
import br.edu.usc.campusiachatbot.enums.OrigemMensagemEnum;
import br.edu.usc.campusiachatbot.enums.StatusAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.TipoSolicitacaoEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatbotServiceRegressionMatrixTest {

    @Test
    void mantemCompraEmAndamentoAoReceberPagamentoQuantidadeERetirada() {
        String mensagem = "Quero 2 unidades dele, vou pagar por PIX e retirar na loja.";
        List<MensagemConversa> historico = List.of(
                new MensagemConversa(DirecaoMensagemEnum.CLIENTE, "Quero comprar Creatina Monoidratada."),
                new MensagemConversa(DirecaoMensagemEnum.BOT, "A Creatina Monoidratada custa R$ 89,90."),
                new MensagemConversa(DirecaoMensagemEnum.CLIENTE, mensagem)
        );
        InterpretacaoIaResponseDTO resposta = processar(
                mensagem,
                historico,
                respostaIa(TipoSolicitacaoEnum.FORMAS_PAGAMENTO, CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO),
                propriedades("Pix e cartao", null)
        );

        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.COMPRA_PRODUTO);
        assertThat(resposta.categoria()).isEqualTo(CategoriaAtendimentoEnum.ATENDIMENTO_COMERCIAL);
        assertThat(resposta.necessitaAtendimentoHumano()).isTrue();
        assertThat(resposta.respostaGerada()).contains("Creatina Monoidratada", "2 unidade(s)", "PIX", "retirada na loja");
        assertTerminologiaCliente(resposta);
    }

    @Test
    void respondeOpiniaoComProdutoEPrecoDoCatalogoSemEndossar() {
        String mensagem = "O controlador de apetite é uma boa não acha?";
        List<MensagemConversa> historico = List.of(
                new MensagemConversa(DirecaoMensagemEnum.CLIENTE, "Quais produtos de Emagrecimento voces tem?"),
                new MensagemConversa(DirecaoMensagemEnum.BOT,
                        "Encontrei no catalogo: Termogenico por R$ 84,90; Controlador de Apetite Natural por R$ 75,50; Detox 10 Dias por R$ 39,90."),
                new MensagemConversa(DirecaoMensagemEnum.CLIENTE, mensagem)
        );
        InterpretacaoIaResponseDTO resposta = processar(
                mensagem,
                historico,
                new InterpretacaoIaResponseDTO(
                        TipoSolicitacaoEnum.DUVIDA_FARMACEUTICA,
                        CategoriaAtendimentoEnum.ATENDIMENTO_FARMACEUTICO,
                        "Sim, e uma excelente escolha.",
                        true,
                        "Encaminhar para analise humana.",
                        92.0
                ),
                propriedades("Pix e cartao", null)
        );

        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.DUVIDA_FARMACEUTICA);
        assertThat(resposta.categoria()).isEqualTo(CategoriaAtendimentoEnum.ATENDIMENTO_FARMACEUTICO);
        assertThat(resposta.necessitaAtendimentoHumano()).isTrue();
        assertThat(resposta.respostaGerada())
                .contains("Controlador de apetite", "R$ 75,50", "Nao posso afirmar", "equipe farmaceutica")
                .contains("consultar mais detalhes", "ajudar com a compra", "colocar voce em contato")
                .doesNotContain("excelente escolha");
        assertTerminologiaCliente(resposta);
    }

    @Test
    void mantemPerguntaIsoladaSobrePixComoAdministrativa() {
        String mensagem = "Voces aceitam PIX?";
        InterpretacaoIaResponseDTO resposta = processar(
                mensagem,
                List.of(new MensagemConversa(DirecaoMensagemEnum.CLIENTE, mensagem)),
                respostaIa(TipoSolicitacaoEnum.FORMAS_PAGAMENTO, CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO),
                propriedades("Pix e cartao", null)
        );

        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.FORMAS_PAGAMENTO);
        assertThat(resposta.categoria()).isEqualTo(CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO);
        assertThat(resposta.necessitaAtendimentoHumano()).isFalse();
        assertThat(resposta.respostaGerada()).contains("Pix e cartao");
    }

    @Test
    void entregaGenericaApresentaTodasAsModalidades() {
        String mensagem = "Como funcionam as entregas?";
        InterpretacaoIaResponseDTO resposta = processar(
                mensagem,
                List.of(new MensagemConversa(DirecaoMensagemEnum.CLIENTE, mensagem)),
                respostaIa(TipoSolicitacaoEnum.ENTREGA, CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO),
                propriedades("Pix", null)
        );

        assertThat(resposta.respostaGerada())
                .contains("Correio", "Transportadora", "todo o Brasil", "Motoboy", "Iacanga", "retirar na loja");
        assertThat(resposta.necessitaAtendimentoHumano()).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cenariosParcelamento")
    void aplicaCondicoesDeParcelamento(
            String cenario,
            String condicoes,
            String textoEsperado,
            boolean humano
    ) {
        String mensagem = "Da para parcelar no cartao?";
        InterpretacaoIaResponseDTO resposta = processar(
                mensagem,
                List.of(new MensagemConversa(DirecaoMensagemEnum.CLIENTE, mensagem)),
                respostaIa(TipoSolicitacaoEnum.FORMAS_PAGAMENTO, CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO),
                propriedades("Pix e cartao", condicoes)
        );

        assertThat(resposta.respostaGerada()).isEqualTo(textoEsperado);
        assertThat(resposta.necessitaAtendimentoHumano()).isEqualTo(humano);
        assertTerminologiaCliente(resposta);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cenariosPagamentoComum")
    void aplicaConfiguracaoDePagamentoComum(
            String cenario,
            String formasPagamento,
            String textoEsperado,
            boolean humano
    ) {
        String mensagem = "Quais formas de pagamento voces aceitam?";
        InterpretacaoIaResponseDTO resposta = processar(
                mensagem,
                List.of(new MensagemConversa(DirecaoMensagemEnum.CLIENTE, mensagem)),
                respostaIa(TipoSolicitacaoEnum.FORMAS_PAGAMENTO, CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO),
                propriedades(formasPagamento, null)
        );

        assertThat(resposta.respostaGerada()).isEqualTo(textoEsperado);
        assertThat(resposta.necessitaAtendimentoHumano()).isEqualTo(humano);
        assertTerminologiaCliente(resposta);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cenariosTerminologiaEncaminhamento")
    void usaTerminologiaDaEquipeEmEncaminhamentos(
            String cenario,
            String mensagem,
            TipoSolicitacaoEnum tipo,
            CategoriaAtendimentoEnum categoria
    ) {
        InterpretacaoIaResponseDTO resposta = processar(
                mensagem,
                List.of(new MensagemConversa(DirecaoMensagemEnum.CLIENTE, mensagem)),
                new InterpretacaoIaResponseDTO(
                        tipo,
                        categoria,
                        "Vou encaminhar seu atendimento para análise humana.",
                        true,
                        "Solicitacao exige atendimento humano.",
                        90.0
                ),
                propriedades(null, null)
        );

        assertThat(resposta.necessitaAtendimentoHumano()).isTrue();
        assertTerminologiaCliente(resposta);
    }

    private static Stream<Arguments> cenariosParcelamento() {
        return Stream.of(
                Arguments.of("parcelamento configurado", "Em ate 3 vezes sem juros.",
                        "Em ate 3 vezes sem juros.", false),
                Arguments.of("parcelamento ausente", null,
                        "Aceitamos cartao, mas a quantidade de parcelas e as condicoes precisam ser confirmadas pela equipe.",
                        true)
        );
    }

    private static Stream<Arguments> cenariosPagamentoComum() {
        return Stream.of(
                Arguments.of("formas configuradas", "Pix e cartao",
                        "Farmacia Teste aceita: Pix e cartao.", false),
                Arguments.of("formas ausentes", null,
                        "As formas de pagamento precisam ser confirmadas pela equipe.", true)
        );
    }

    private static Stream<Arguments> cenariosTerminologiaEncaminhamento() {
        return Stream.of(
                Arguments.of("dose", "Qual dose devo tomar?",
                        TipoSolicitacaoEnum.DUVIDA_FARMACEUTICA,
                        CategoriaAtendimentoEnum.ATENDIMENTO_FARMACEUTICO),
                Arguments.of("contraindicacao", "Esse produto tem contraindicacao?",
                        TipoSolicitacaoEnum.DUVIDA_FARMACEUTICA,
                        CategoriaAtendimentoEnum.ATENDIMENTO_FARMACEUTICO),
                Arguments.of("reclamacao", "Quero reclamar do atraso do pedido",
                        TipoSolicitacaoEnum.RECLAMACAO,
                        CategoriaAtendimentoEnum.RECLAMACAO),
                Arguments.of("status", "Preciso do status do pedido",
                        TipoSolicitacaoEnum.STATUS_PEDIDO,
                        CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO)
        );
    }

    private InterpretacaoIaResponseDTO processar(
            String mensagem,
            List<MensagemConversa> historico,
            InterpretacaoIaResponseDTO respostaIa,
            EstabelecimentoProperties propriedades
    ) {
        InterpretacaoIaService interpretacao = mock(InterpretacaoIaService.class);
        AtendimentoService atendimentos = mock(AtendimentoService.class);
        EnderecoEnrichmentService enderecos = mock(EnderecoEnrichmentService.class);
        CepLookupClient cep = mock(CepLookupClient.class);
        ChatbotMessagesProperties mensagens = new ChatbotMessagesProperties(
                "{nome}: {horario}",
                "O horario precisa ser confirmado pela equipe.",
                "{nome} aceita: {formasPagamento}.",
                "As formas de pagamento precisam ser confirmadas pela equipe.",
                "{nome}: {endereco}",
                "O endereco precisa ser confirmado pela equipe.",
                "Informe seu CEP.",
                "Entregamos em {cidade}.",
                "Motoboy somente em {cidades}.",
                "Motoboy em {cidades}.",
                "Informe a cidade."
        );
        ChatbotService service = new ChatbotService(
                interpretacao,
                atendimentos,
                () -> propriedades,
                mensagens,
                new ChatbotSessionProperties(30, 10),
                cep,
                enderecos
        );
        ChatbotRequestDTO request = new ChatbotRequestDTO(
                "14999999999", "Cliente", mensagem, OrigemMensagemEnum.SIMULADOR);
        Atendimento atendimento = new Atendimento(
                "42", null, request.telefoneCliente(), request.nomeCliente(), request.origem(), mensagem,
                null, null, null, false, null, null, StatusAtendimentoEnum.RECEBIDO,
                LocalDateTime.of(2026, 9, 12, 10, 0)
        );
        EnderecoEnriquecidoDTO endereco = EnderecoEnriquecidoDTO.vazio();
        when(atendimentos.buscarConversaAtiva(request.telefoneCliente(), 30)).thenReturn(Optional.empty());
        when(atendimentos.criarRecebido(request, OrigemMensagemEnum.SIMULADOR)).thenReturn(atendimento);
        when(atendimentos.buscarHistoricoMensagens("42", 10)).thenReturn(historico);
        when(enderecos.enriquecer(mensagem)).thenReturn(endereco);
        when(interpretacao.interpretarMensagem(request, endereco, historico)).thenReturn(respostaIa);
        when(atendimentos.atualizarComResposta(eq(atendimento), any(), any())).thenReturn(atendimento);

        service.processarMensagem(request, OrigemMensagemEnum.SIMULADOR);

        ArgumentCaptor<InterpretacaoIaResponseDTO> captor = ArgumentCaptor.forClass(InterpretacaoIaResponseDTO.class);
        verify(atendimentos).atualizarComResposta(eq(atendimento), captor.capture(), any());
        return captor.getValue();
    }

    private InterpretacaoIaResponseDTO respostaIa(
            TipoSolicitacaoEnum tipo,
            CategoriaAtendimentoEnum categoria
    ) {
        return new InterpretacaoIaResponseDTO(tipo, categoria, "Resposta da IA.", false, null, 90.0);
    }

    private static void assertTerminologiaCliente(InterpretacaoIaResponseDTO resposta) {
        assertThat(resposta.respostaGerada()).doesNotContainIgnoringCase("humano", "humana");
        assertThat(resposta.motivoEncaminhamento() == null ? "" : resposta.motivoEncaminhamento())
                .doesNotContainIgnoringCase("humano", "humana");
    }

    private EstabelecimentoProperties propriedades(String formasPagamento, String condicoesParcelamento) {
        return new EstabelecimentoProperties(
                "Farmacia Teste",
                "farmacia",
                "08:00 as 18:00",
                "Rua Teste",
                formasPagamento,
                condicoesParcelamento,
                "Entregamos apenas nas cidades atendidas configuradas.",
                List.of("Iacanga"),
                "SP",
                "14999999999",
                "https://catalogo.example"
        );
    }
}
