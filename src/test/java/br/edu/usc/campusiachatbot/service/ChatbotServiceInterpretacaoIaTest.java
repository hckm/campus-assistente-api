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
import br.edu.usc.campusiachatbot.exception.GlobalExceptionHandler;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatbotServiceInterpretacaoIaTest {

    private final InterpretacaoIaService interpretacaoIa = mock(InterpretacaoIaService.class);
    private final AtendimentoService atendimentos = mock(AtendimentoService.class);
    private final EnderecoEnrichmentService enderecos = mock(EnderecoEnrichmentService.class);
    private final ChatbotService service = new ChatbotService(
            interpretacaoIa,
            atendimentos,
            () -> mock(EstabelecimentoProperties.class),
            mock(ChatbotMessagesProperties.class),
            new ChatbotSessionProperties(30, 10),
            mock(CepLookupClient.class),
            enderecos);
    private final ChatbotRequestDTO request = new ChatbotRequestDTO(
            "14999999999", "Teste", "Qual a dose?", OrigemMensagemEnum.SIMULADOR);
    private final Atendimento atendimento = new Atendimento(
            "42",
            null,
            request.telefoneCliente(),
            request.nomeCliente(),
            request.origem(),
            request.mensagem(),
            null,
            null,
            null,
            false,
            null,
            null,
            StatusAtendimentoEnum.RECEBIDO,
            LocalDateTime.of(2026, 1, 2, 10, 30)
    );
    private final EnderecoEnriquecidoDTO endereco = EnderecoEnriquecidoDTO.vazio();
    private final List<MensagemConversa> historico = List.of(
            new MensagemConversa(DirecaoMensagemEnum.CLIENTE, "Ola"),
            new MensagemConversa(DirecaoMensagemEnum.BOT, "Como posso ajudar?"),
            new MensagemConversa(DirecaoMensagemEnum.CLIENTE, request.mensagem()));

    @BeforeEach
    void prepararAtendimento() {
        when(atendimentos.buscarConversaAtiva(request.telefoneCliente(), 30)).thenReturn(Optional.empty());
        when(atendimentos.criarRecebido(request, OrigemMensagemEnum.SIMULADOR)).thenReturn(atendimento);
        when(atendimentos.buscarHistoricoMensagens("42", 10)).thenReturn(historico);
        when(enderecos.enriquecer(request.mensagem())).thenReturn(endereco);
    }

    @Test
    void devePassarContextoParaInterfaceEAplicarSegurancaAntesDePersistir() {
        when(interpretacaoIa.interpretarMensagem(request, endereco, historico)).thenReturn(
                new InterpretacaoIaResponseDTO(TipoSolicitacaoEnum.DUVIDA_FARMACEUTICA,
                        CategoriaAtendimentoEnum.ATENDIMENTO_FARMACEUTICO,
                        "Resposta clinica que deve ser substituida", false, null, 0.9));
        when(atendimentos.atualizarComResposta(eq(atendimento), any(),
                eq(StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA))).thenReturn(atendimento);

        service.processarMensagem(request, OrigemMensagemEnum.SIMULADOR);

        ArgumentCaptor<InterpretacaoIaResponseDTO> resposta = ArgumentCaptor.forClass(InterpretacaoIaResponseDTO.class);
        verify(interpretacaoIa).interpretarMensagem(request, endereco, historico);
        verify(atendimentos).atualizarComResposta(eq(atendimento), resposta.capture(),
                eq(StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA));
        assertThat(resposta.getValue().necessitaAtendimentoHumano()).isTrue();
        assertThat(resposta.getValue().respostaGerada()).contains("equipe farmaceutica", "farmaceutico")
                .doesNotContainIgnoringCase("humano")
                .doesNotContain("Resposta clinica que deve ser substituida");
        assertThat(resposta.getValue().motivoEncaminhamento()).isNotBlank();
        assertThat(resposta.getValue().confianca()).isEqualTo(90.0);
        verify(atendimentos, never()).marcarErro(any());
    }

    @Test
    void devePreservarExcecaoOriginalSemAnexarFalhaAoMarcarErro() {
        RuntimeException falhaOriginal = new IllegalStateException("Falha na interpretacao");
        RuntimeException falhaPersistencia = new IllegalStateException("Falha ao registrar erro");
        when(interpretacaoIa.interpretarMensagem(request, endereco, historico)).thenThrow(falhaOriginal);
        doThrow(falhaPersistencia).when(atendimentos).marcarErro(atendimento);

        assertThatThrownBy(() -> service.processarMensagem(request, OrigemMensagemEnum.SIMULADOR))
                .isSameAs(falhaOriginal);

        assertThat(falhaOriginal.getSuppressed()).isEmpty();
        verify(atendimentos).marcarErro(atendimento);
        verify(atendimentos, never()).atualizarComResposta(any(), any(), any());
    }

    @Test
    void devePreservarExcecaoQuandoMarcacaoDeErroRelancaAMesmaInstancia() {
        RuntimeException falhaOriginal = new IllegalStateException("Falha compartilhada");
        when(interpretacaoIa.interpretarMensagem(request, endereco, historico)).thenThrow(falhaOriginal);
        doThrow(falhaOriginal).when(atendimentos).marcarErro(atendimento);

        assertThatThrownBy(() -> service.processarMensagem(request, OrigemMensagemEnum.SIMULADOR))
                .isSameAs(falhaOriginal);

        assertThat(falhaOriginal.getSuppressed()).isEmpty();
    }

    @Test
    void deveDiagnosticarFalhaSecundariaSemExporConteudoNoLogOuTratamentoPosterior() {
        String marcador = "QA_SYNTHETIC_PRIVATE_CONTENT";
        RuntimeException falhaOriginal = new IllegalStateException("Falha na interpretacao");
        RuntimeException falhaPersistencia = new DataIntegrityViolationException(
                "ERROR: Failing row contains (" + marcador + ")",
                new IllegalStateException(marcador + "_CAUSE"));
        falhaPersistencia.addSuppressed(new IllegalStateException(marcador + "_SUPPRESSED"));
        when(interpretacaoIa.interpretarMensagem(request, endereco, historico)).thenThrow(falhaOriginal);
        doThrow(falhaPersistencia).when(atendimentos).marcarErro(atendimento);
        Logger logger = (Logger) LoggerFactory.getLogger(ChatbotService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            assertThatThrownBy(() -> service.processarMensagem(request, OrigemMensagemEnum.SIMULADOR))
                    .isSameAs(falhaOriginal);

            String logsRenderizados = appender.list.stream()
                    .map(evento -> evento.getFormattedMessage() + (evento.getThrowableProxy() == null
                            ? "" : ThrowableProxyUtil.asString(evento.getThrowableProxy())))
                    .reduce("", String::concat);
            assertThat(logsRenderizados).doesNotContain(marcador);
            assertThat(appender.list).anySatisfy(evento -> {
                assertThat(evento.getLevel()).isEqualTo(Level.WARN);
                assertThat(evento.getFormattedMessage()).contains("42", "marcar erro",
                        DataIntegrityViolationException.class.getName());
                assertThat(evento.getThrowableProxy()).isNull();
            });
            assertThat(appender.list).anySatisfy(evento -> {
                assertThat(evento.getLevel()).isEqualTo(Level.ERROR);
                assertThat(evento.getThrowableProxy()).isNotNull();
                assertThat(evento.getThrowableProxy().getClassName()).isEqualTo(falhaOriginal.getClass().getName());
            });

            StringWriter diagnosticoPosterior = new StringWriter();
            falhaOriginal.printStackTrace(new PrintWriter(diagnosticoPosterior));
            assertThat(diagnosticoPosterior.toString()).doesNotContain(marcador);
            assertThat(falhaOriginal.getSuppressed()).isEmpty();
            var resposta = new GlobalExceptionHandler().handleUnexpected(falhaOriginal,
                    new MockHttpServletRequest("POST", "/api/portal/chat"));
            assertThat(resposta.getStatusCode().value()).isEqualTo(500);
            assertThat(resposta.getBody()).isNotNull();
            assertThat(resposta.getBody().toString()).doesNotContain(marcador);
            verify(atendimentos).marcarErro(atendimento);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
