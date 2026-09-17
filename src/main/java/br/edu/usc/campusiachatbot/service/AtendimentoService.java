package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.config.ChatbotSessionProperties;
import br.edu.usc.campusiachatbot.domain.Atendimento;
import br.edu.usc.campusiachatbot.domain.InicioInteracao;
import br.edu.usc.campusiachatbot.domain.MensagemConversa;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.ChatbotResponseDTO;
import br.edu.usc.campusiachatbot.dto.InterpretacaoIaResponseDTO;
import br.edu.usc.campusiachatbot.dto.PaginaResponseDTO;
import br.edu.usc.campusiachatbot.enums.OrigemMensagemEnum;
import br.edu.usc.campusiachatbot.enums.StatusAtendimentoEnum;
import br.edu.usc.campusiachatbot.exception.AtendimentoNotFoundException;
import br.edu.usc.campusiachatbot.repository.ClienteChave;
import br.edu.usc.campusiachatbot.store.AtendimentoStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AtendimentoService {

    private static final List<StatusAtendimentoEnum> STATUS_CONVERSA_ATIVA = List.of(
            StatusAtendimentoEnum.RECEBIDO,
            StatusAtendimentoEnum.PROCESSADO,
            StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA
    );

    private final AtendimentoStore atendimentoStore;
    private final ChatbotSessionProperties sessionProperties;
    private final Clock clock;

    public Atendimento criarRecebido(ChatbotRequestDTO request, OrigemMensagemEnum origemPadrao) {
        return iniciarInteracao(request, origemPadrao, null).atendimento();
    }

    public InicioInteracao iniciarInteracao(
            ChatbotRequestDTO request,
            OrigemMensagemEnum origemPadrao,
            String idempotencyKey
    ) {
        validarIdempotencyKey(idempotencyKey);
        LocalDateTime agora = agora();
        Atendimento atendimento = new Atendimento(
                null,
                ClienteChave.criar(request.telefoneCliente()),
                request.telefoneCliente(),
                request.nomeCliente(),
                request.origemOuDefault(origemPadrao),
                request.mensagem(),
                null,
                null,
                null,
                false,
                null,
                null,
                StatusAtendimentoEnum.RECEBIDO,
                agora
        );
        return atendimentoStore.iniciarInteracao(
                atendimento,
                STATUS_CONVERSA_ATIVA,
                agora.minusMinutes(sessionProperties.janelaInatividadeMinutos()),
                agora.plusSeconds(sessionProperties.processamentoExpiracaoSegundos()),
                normalizarIdempotencyKey(idempotencyKey),
                payloadHash(request, origemPadrao)
        );
    }

    public Optional<Atendimento> buscarConversaAtiva(String telefoneCliente, int janelaMinutos) {
        LocalDateTime limite = agora().minusMinutes(janelaMinutos);
        return atendimentoStore.buscarConversaAtiva(telefoneCliente, STATUS_CONVERSA_ATIVA, limite);
    }

    public Atendimento adicionarMensagemCliente(Atendimento atendimento, ChatbotRequestDTO request) {
        return iniciarInteracao(request, atendimento.origem(), null).atendimento();
    }

    public List<MensagemConversa> buscarHistoricoMensagens(String atendimentoId, int limite) {
        Atendimento atendimento = atendimentoStore.buscarPorId(atendimentoId)
                .orElseThrow(() -> new AtendimentoNotFoundException(atendimentoId));
        return atendimentoStore.buscarHistoricoMensagens(atendimento, limite);
    }

    public List<MensagemConversa> buscarHistoricoInteracao(InicioInteracao interacao, int limite) {
        return atendimentoStore.buscarHistoricoMensagens(interacao.atendimento(), limite);
    }

    public Atendimento atualizarComResposta(
            Atendimento atendimento,
            InterpretacaoIaResponseDTO resposta,
            StatusAtendimentoEnum status
    ) {
        return atualizarComRespostaInteracao(new InicioInteracao(atendimento, null, null, false), resposta, status);
    }

    public Atendimento atualizarComRespostaInteracao(
            InicioInteracao interacao,
            InterpretacaoIaResponseDTO resposta,
            StatusAtendimentoEnum status
    ) {
        Atendimento atendimento = interacao.atendimento();
        boolean aguardandoAnaliseHumana = atendimento.status() == StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA;
        Atendimento atualizado = new Atendimento(
                atendimento.id(),
                atendimento.clienteChave(),
                atendimento.telefoneCliente(),
                atendimento.nomeCliente(),
                atendimento.origem(),
                atendimento.mensagemCliente(),
                resposta.tipoSolicitacao(),
                resposta.categoria(),
                resposta.respostaGerada(),
                aguardandoAnaliseHumana || resposta.necessitaAtendimentoHumano(),
                aguardandoAnaliseHumana ? atendimento.motivoEncaminhamento() : resposta.motivoEncaminhamento(),
                resposta.confianca(),
                aguardandoAnaliseHumana ? StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA : status,
                agora()
        );
        return atendimentoStore.registrarRespostaComMensagemBot(interacao, atualizado);
    }

    public void marcarErro(Atendimento atendimento) {
        atendimentoStore.marcarErro(new InicioInteracao(atendimento, null, null, false), agora());
    }

    public void marcarErroInteracao(InicioInteracao interacao) {
        atendimentoStore.marcarErro(interacao, agora());
    }

    public List<ChatbotResponseDTO> listarTodos() {
        return atendimentoStore.listarTodosOrdenadosPorDataProcessamentoDesc(null, 100).itens()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public PaginaResponseDTO<ChatbotResponseDTO> listarTodos(String token, int tamanho) {
        validarTamanhoPagina(tamanho);
        var pagina = atendimentoStore.listarTodosOrdenadosPorDataProcessamentoDesc(token, tamanho);
        return new PaginaResponseDTO<>(pagina.itens().stream().map(this::toResponseDTO).toList(), pagina.proximoToken());
    }

    public ChatbotResponseDTO buscarPorId(String id) {
        return atendimentoStore.buscarPorId(id)
                .map(this::toResponseDTO)
                .orElseThrow(() -> new AtendimentoNotFoundException(id));
    }

    public List<ChatbotResponseDTO> listarPorStatus(StatusAtendimentoEnum status) {
        return atendimentoStore.listarPorStatusOrdenadosPorDataProcessamentoDesc(status, null, 100).itens()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public PaginaResponseDTO<ChatbotResponseDTO> listarPorStatus(
            StatusAtendimentoEnum status,
            String token,
            int tamanho
    ) {
        validarTamanhoPagina(tamanho);
        var pagina = atendimentoStore.listarPorStatusOrdenadosPorDataProcessamentoDesc(status, token, tamanho);
        return new PaginaResponseDTO<>(pagina.itens().stream().map(this::toResponseDTO).toList(), pagina.proximoToken());
    }

    public ChatbotResponseDTO toResponseDTO(Atendimento atendimento) {
        return new ChatbotResponseDTO(
                atendimento.id(),
                atendimento.origem(),
                atendimento.mensagemCliente(),
                atendimento.tipoSolicitacao(),
                atendimento.categoria(),
                atendimento.respostaGerada(),
                atendimento.necessitaAtendimentoHumano(),
                atendimento.motivoEncaminhamento(),
                atendimento.confianca(),
                atendimento.status(),
                atendimento.dataProcessamento()
        );
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock);
    }

    private void validarIdempotencyKey(String value) {
        if (value != null && (value.isBlank() || value.length() > 200)) {
            throw new IllegalArgumentException("Idempotency-Key deve ter entre 1 e 200 caracteres");
        }
    }

    private String normalizarIdempotencyKey(String value) {
        return value == null ? null : value.trim();
    }

    private String payloadHash(ChatbotRequestDTO request, OrigemMensagemEnum origemPadrao) {
        String payload = ClienteChave.canonicalizar(request.telefoneCliente()) + "\u001f"
                + String.valueOf(request.nomeCliente()) + "\u001f"
                + request.mensagem() + "\u001f"
                + request.origemOuDefault(origemPadrao).name();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponivel", exception);
        }
    }

    private void validarTamanhoPagina(int tamanho) {
        if (tamanho < 1 || tamanho > 100) {
            throw new IllegalArgumentException("tamanho deve estar entre 1 e 100");
        }
    }
}
