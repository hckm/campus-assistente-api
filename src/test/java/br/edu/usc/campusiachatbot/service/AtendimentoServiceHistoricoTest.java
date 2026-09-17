package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.domain.Atendimento;
import br.edu.usc.campusiachatbot.domain.MensagemConversa;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.ChatbotResponseDTO;
import br.edu.usc.campusiachatbot.dto.InterpretacaoIaResponseDTO;
import br.edu.usc.campusiachatbot.enums.CategoriaAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.DirecaoMensagemEnum;
import br.edu.usc.campusiachatbot.enums.OrigemMensagemEnum;
import br.edu.usc.campusiachatbot.enums.StatusAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.TipoSolicitacaoEnum;
import br.edu.usc.campusiachatbot.repository.AtendimentoRepository;
import br.edu.usc.campusiachatbot.repository.MensagemAtendimentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "gemini.api-key=",
        "spring.datasource.url=jdbc:h2:mem:atendimento_service;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class AtendimentoServiceHistoricoTest {

    @Autowired
    private AtendimentoService atendimentoService;

    @Autowired
    private AtendimentoRepository atendimentoRepository;

    @Autowired
    private MensagemAtendimentoRepository mensagemAtendimentoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limparBase() {
        mensagemAtendimentoRepository.deleteAll();
        atendimentoRepository.deleteAll();
    }

    @Test
    void deveCriarContinuarELimitarHistoricoEmOrdemCronologica() {
        Atendimento atendimento = atendimentoService.criarRecebido(
                request("Primeira mensagem", "Nome inicial"),
                OrigemMensagemEnum.SIMULADOR
        );
        atendimento = atendimentoService.atualizarComResposta(
                atendimento,
                resposta("Primeira resposta"),
                StatusAtendimentoEnum.PROCESSADO
        );
        atendimento = atendimentoService.adicionarMensagemCliente(
                atendimento,
                request("Segunda mensagem", "Nome atualizado")
        );
        atendimento = atendimentoService.atualizarComResposta(
                atendimento,
                resposta("Segunda resposta"),
                StatusAtendimentoEnum.PROCESSADO
        );
        atendimento = atendimentoService.adicionarMensagemCliente(
                atendimento,
                request("Terceira mensagem", "")
        );

        List<MensagemConversa> historico = atendimentoService.buscarHistoricoMensagens(atendimento.id(), 3);

        assertThat(atendimentoRepository.count()).isEqualTo(1);
        assertThat(mensagemAtendimentoRepository.count()).isEqualTo(5);
        assertThat(atendimento.nomeCliente()).isEqualTo("Nome atualizado");
        assertThat(atendimento.status()).isEqualTo(StatusAtendimentoEnum.RECEBIDO);
        assertThat(historico).containsExactly(
                new MensagemConversa(DirecaoMensagemEnum.CLIENTE, "Segunda mensagem"),
                new MensagemConversa(DirecaoMensagemEnum.BOT, "Segunda resposta"),
                new MensagemConversa(DirecaoMensagemEnum.CLIENTE, "Terceira mensagem")
        );
    }

    @Test
    void deveReverterResumoQuandoMensagemBotFalha() {
        Atendimento atendimento = atendimentoService.criarRecebido(
                request("Mensagem inicial", "Cliente"),
                OrigemMensagemEnum.SIMULADOR
        );
        jdbcTemplate.execute(
                "ALTER TABLE mensagens_atendimento ADD CONSTRAINT ck_mensagem_conteudo_teste CHECK (CHAR_LENGTH(conteudo) <= 20)"
        );

        try {
            assertThatThrownBy(() -> atendimentoService.atualizarComResposta(
                    atendimento,
                    resposta("Resposta de bot maior que vinte caracteres"),
                    StatusAtendimentoEnum.PROCESSADO
            )).isInstanceOf(DataIntegrityViolationException.class);

            var persistido = atendimentoRepository.findById(Long.valueOf(atendimento.id())).orElseThrow();
            assertThat(persistido.getStatus()).isEqualTo(StatusAtendimentoEnum.RECEBIDO);
            assertThat(persistido.getTipoSolicitacao()).isNull();
            assertThat(persistido.getRespostaGerada()).isNull();
            assertThat(mensagemAtendimentoRepository.count()).isEqualTo(1);
        } finally {
            jdbcTemplate.execute(
                    "ALTER TABLE mensagens_atendimento DROP CONSTRAINT ck_mensagem_conteudo_teste"
            );
        }
    }

    @Test
    void devePreservarResumoSessaoFiltrosEOrdenacao() {
        Atendimento primeiro = atendimentoService.criarRecebido(
                request("Primeiro atendimento", "Primeiro cliente"),
                OrigemMensagemEnum.SIMULADOR
        );
        primeiro = atendimentoService.atualizarComResposta(
                primeiro,
                resposta("Resposta persistida"),
                StatusAtendimentoEnum.PROCESSADO
        );
        Atendimento segundo = atendimentoService.criarRecebido(
                new ChatbotRequestDTO(
                        "14888888888",
                        null,
                        "Segundo atendimento",
                        OrigemMensagemEnum.WHATSAPP
                ),
                OrigemMensagemEnum.SIMULADOR
        );

        List<ChatbotResponseDTO> todos = atendimentoService.listarTodos();
        ChatbotResponseDTO consultado = atendimentoService.buscarPorId(primeiro.id());
        List<ChatbotResponseDTO> processados = atendimentoService.listarPorStatus(
                StatusAtendimentoEnum.PROCESSADO
        );

        assertThat(todos).extracting(ChatbotResponseDTO::idAtendimento)
                .containsExactly(segundo.id(), primeiro.id());
        assertThat(consultado.origem()).isEqualTo(OrigemMensagemEnum.WHATSAPP);
        assertThat(consultado.mensagemCliente()).isEqualTo("Primeiro atendimento");
        assertThat(consultado.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.DUVIDA_ADMINISTRATIVA);
        assertThat(consultado.categoria()).isEqualTo(CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO);
        assertThat(consultado.respostaGerada()).isEqualTo("Resposta persistida");
        assertThat(consultado.necessitaAtendimentoHumano()).isFalse();
        assertThat(consultado.motivoEncaminhamento()).isNull();
        assertThat(consultado.confianca()).isEqualTo(95.0);
        assertThat(consultado.status()).isEqualTo(StatusAtendimentoEnum.PROCESSADO);
        assertThat(consultado.dataProcessamento()).isNotNull();
        assertThat(processados).extracting(ChatbotResponseDTO::idAtendimento)
                .containsExactly(primeiro.id());
        Atendimento conversaAtiva = atendimentoService.buscarConversaAtiva(primeiro.telefoneCliente(), 30)
                .orElseThrow();
        assertThat(conversaAtiva.id()).isEqualTo(primeiro.id());
        assertThat(conversaAtiva.status()).isEqualTo(StatusAtendimentoEnum.PROCESSADO);
        assertThat(conversaAtiva.dataProcessamento()).isNotNull();

        Atendimento aguardandoHumano = atendimentoService.atualizarComResposta(
                primeiro,
                resposta("Encaminhado"),
                StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA
        );

        assertThat(aguardandoHumano.status()).isEqualTo(StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA);
        assertThat(atendimentoService.buscarConversaAtiva(primeiro.telefoneCliente(), 30))
                .map(Atendimento::id)
                .contains(primeiro.id());
    }

    @Test
    void deveManterEncaminhamentoEHistoricoQuandoClienteContinuaAConversa() {
        Atendimento atendimento = atendimentoService.criarRecebido(
                request("Mensagem inicial", "Cliente"),
                OrigemMensagemEnum.PORTAL
        );
        atendimento = atendimentoService.atualizarComResposta(
                atendimento,
                resposta("Resposta inicial"),
                StatusAtendimentoEnum.PROCESSADO
        );
        String motivoOriginal = "Solicitacao precisa de avaliacao da equipe responsavel.";
        atendimento = atendimentoService.adicionarMensagemCliente(
                atendimento,
                request("Preciso saber a dose para uma crianca", "Cliente")
        );
        atendimento = atendimentoService.atualizarComResposta(
                atendimento,
                respostaEncaminhamento("Vou encaminhar sua solicitacao.", motivoOriginal),
                StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA
        );

        Atendimento continuacao = atendimentoService.adicionarMensagemCliente(
                atendimento,
                request("Qual o horario de funcionamento?", "Cliente")
        );
        Atendimento atualizado = atendimentoService.atualizarComResposta(
                continuacao,
                resposta("Atendemos em horario comercial."),
                StatusAtendimentoEnum.PROCESSADO
        );

        assertThat(continuacao.id()).isEqualTo(atendimento.id());
        assertThat(continuacao.status()).isEqualTo(StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA);
        assertThat(atualizado.id()).isEqualTo(atendimento.id());
        assertThat(atualizado.status()).isEqualTo(StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA);
        assertThat(atualizado.necessitaAtendimentoHumano()).isTrue();
        assertThat(atualizado.motivoEncaminhamento()).isEqualTo(motivoOriginal);
        assertThat(atendimentoService.buscarHistoricoMensagens(atualizado.id(), 10)).containsExactly(
                new MensagemConversa(DirecaoMensagemEnum.CLIENTE, "Mensagem inicial"),
                new MensagemConversa(DirecaoMensagemEnum.BOT, "Resposta inicial"),
                new MensagemConversa(DirecaoMensagemEnum.CLIENTE, "Preciso saber a dose para uma crianca"),
                new MensagemConversa(DirecaoMensagemEnum.BOT, "Vou encaminhar sua solicitacao."),
                new MensagemConversa(DirecaoMensagemEnum.CLIENTE, "Qual o horario de funcionamento?"),
                new MensagemConversa(DirecaoMensagemEnum.BOT, "Atendemos em horario comercial.")
        );
    }

    @Test
    void deveCriarNovoAtendimentoAposFinalizacaoErroInatividadeEOuSessaoDiferente() {
        Atendimento inicial = atendimentoService.criarRecebido(
                request("Mensagem inicial", "Cliente"),
                OrigemMensagemEnum.PORTAL
        );
        Atendimento finalizado = atendimentoService.atualizarComResposta(
                inicial,
                resposta("Atendimento finalizado."),
                StatusAtendimentoEnum.FINALIZADO
        );

        assertThat(atendimentoService.buscarConversaAtiva(finalizado.telefoneCliente(), 30)).isEmpty();
        Atendimento aposFinalizacao = atendimentoService.criarRecebido(
                request("Nova mensagem", "Cliente"),
                OrigemMensagemEnum.PORTAL
        );
        assertThat(aposFinalizacao.id()).isNotEqualTo(finalizado.id());

        atendimentoService.marcarErro(aposFinalizacao);
        assertThat(atendimentoService.buscarConversaAtiva(aposFinalizacao.telefoneCliente(), 30)).isEmpty();
        Atendimento aposErro = atendimentoService.criarRecebido(
                request("Mensagem apos erro", "Cliente"),
                OrigemMensagemEnum.PORTAL
        );
        assertThat(aposErro.id()).isNotEqualTo(aposFinalizacao.id());

        var entityExpirada = atendimentoRepository.findById(Long.valueOf(aposErro.id())).orElseThrow();
        entityExpirada.setDataProcessamento(LocalDateTime.now().minusMinutes(31));
        atendimentoRepository.save(entityExpirada);
        assertThat(atendimentoService.buscarConversaAtiva(aposErro.telefoneCliente(), 30)).isEmpty();
        Atendimento aposInatividade = atendimentoService.criarRecebido(
                request("Mensagem apos inatividade", "Cliente"),
                OrigemMensagemEnum.PORTAL
        );
        assertThat(aposInatividade.id()).isNotEqualTo(aposErro.id());

        Atendimento outraSessao = atendimentoService.criarRecebido(
                new ChatbotRequestDTO("14999999998", null, "Nova sessao do portal", OrigemMensagemEnum.PORTAL),
                OrigemMensagemEnum.PORTAL
        );
        assertThat(outraSessao.id()).isNotEqualTo(aposInatividade.id());
    }

    private ChatbotRequestDTO request(String mensagem, String nome) {
        return new ChatbotRequestDTO(
                "14999999999",
                nome,
                mensagem,
                OrigemMensagemEnum.WHATSAPP
        );
    }

    private InterpretacaoIaResponseDTO resposta(String conteudo) {
        return new InterpretacaoIaResponseDTO(
                TipoSolicitacaoEnum.DUVIDA_ADMINISTRATIVA,
                CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                conteudo,
                false,
                null,
                95.0
        );
    }

    private InterpretacaoIaResponseDTO respostaEncaminhamento(String conteudo, String motivo) {
        return new InterpretacaoIaResponseDTO(
                TipoSolicitacaoEnum.DUVIDA_FARMACEUTICA,
                CategoriaAtendimentoEnum.ATENDIMENTO_FARMACEUTICO,
                conteudo,
                true,
                motivo,
                95.0
        );
    }
}
