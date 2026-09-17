package br.edu.usc.campusiachatbot.repository;

import br.edu.usc.campusiachatbot.domain.Atendimento;
import br.edu.usc.campusiachatbot.domain.InicioInteracao;
import br.edu.usc.campusiachatbot.domain.MensagemConversa;
import br.edu.usc.campusiachatbot.domain.Pagina;
import br.edu.usc.campusiachatbot.entity.AtendimentoEntity;
import br.edu.usc.campusiachatbot.entity.MensagemAtendimentoEntity;
import br.edu.usc.campusiachatbot.enums.DirecaoMensagemEnum;
import br.edu.usc.campusiachatbot.enums.StatusAtendimentoEnum;
import br.edu.usc.campusiachatbot.store.AtendimentoStore;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "persistencia", name = "backend", havingValue = "JPA", matchIfMissing = true)
public class JpaAtendimentoStore implements AtendimentoStore {

    private final AtendimentoRepository atendimentoRepository;
    private final MensagemAtendimentoRepository mensagemAtendimentoRepository;

    @Override
    @Transactional
    public InicioInteracao iniciarInteracao(
            Atendimento atendimento,
            List<StatusAtendimentoEnum> statuses,
            LocalDateTime limite,
            LocalDateTime processamentoExpiraEm,
            String idempotencyKey,
            String payloadHash
    ) {
        if (idempotencyKey != null) {
            throw new IllegalArgumentException("Idempotency-Key exige o backend Cosmos");
        }
        Optional<AtendimentoEntity> ativa = atendimentoRepository
                .findFirstByTelefoneClienteAndStatusInAndDataProcessamentoAfterOrderByDataProcessamentoDesc(
                        atendimento.telefoneCliente(), statuses, limite
                );
        Atendimento salvo = ativa.isPresent()
                ? adicionarMensagem(ativa.get(), atendimento)
                : criarAtendimento(atendimento);
        return new InicioInteracao(salvo, null, null, false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Atendimento> buscarConversaAtiva(
            String telefoneCliente,
            List<StatusAtendimentoEnum> statuses,
            LocalDateTime limite
    ) {
        return atendimentoRepository
                .findFirstByTelefoneClienteAndStatusInAndDataProcessamentoAfterOrderByDataProcessamentoDesc(
                        telefoneCliente, statuses, limite
                )
                .map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MensagemConversa> buscarHistoricoMensagens(Atendimento atendimento, int limite) {
        var pageable = PageRequest.of(0, limite, Sort.by(Sort.Direction.DESC, "dataMensagem"));
        List<MensagemAtendimentoEntity> mensagens = mensagemAtendimentoRepository.findUltimasMensagens(
                toJpaId(atendimento.id()), pageable
        );
        List<MensagemAtendimentoEntity> cronologico = new ArrayList<>(mensagens);
        Collections.reverse(cronologico);
        return cronologico.stream()
                .map(mensagem -> new MensagemConversa(mensagem.getDirecao(), mensagem.getConteudo()))
                .toList();
    }

    @Override
    @Transactional
    public Atendimento registrarRespostaComMensagemBot(InicioInteracao interacao, Atendimento atendimento) {
        AtendimentoEntity entity = buscarEntity(atendimento.id());
        entity.setTipoSolicitacao(atendimento.tipoSolicitacao());
        entity.setCategoria(atendimento.categoria());
        entity.setRespostaGerada(atendimento.respostaGerada());
        entity.setNecessitaAtendimentoHumano(atendimento.necessitaAtendimentoHumano());
        entity.setMotivoEncaminhamento(atendimento.motivoEncaminhamento());
        entity.setConfianca(atendimento.confianca());
        entity.setStatus(atendimento.status());
        entity.setDataProcessamento(atendimento.dataProcessamento());
        entity.getMensagens().add(toMensagemEntity(
                entity, DirecaoMensagemEnum.BOT, atendimento.respostaGerada(), atendimento.dataProcessamento()
        ));
        return toDomain(atendimentoRepository.save(entity));
    }

    @Override
    @Transactional
    public void marcarErro(InicioInteracao interacao, LocalDateTime dataProcessamento) {
        AtendimentoEntity entity = buscarEntity(interacao.atendimento().id());
        entity.setStatus(StatusAtendimentoEnum.ERRO_PROCESSAMENTO);
        entity.setDataProcessamento(dataProcessamento);
        atendimentoRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<Atendimento> listarTodosOrdenadosPorDataProcessamentoDesc(String token, int tamanho) {
        int numero = numeroPagina(token);
        return pagina(atendimentoRepository.findAll(pageable(numero, tamanho)), numero);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Atendimento> buscarPorId(String id) {
        return atendimentoRepository.findById(toJpaId(id)).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<Atendimento> listarPorStatusOrdenadosPorDataProcessamentoDesc(
            StatusAtendimentoEnum status,
            String token,
            int tamanho
    ) {
        int numero = numeroPagina(token);
        return pagina(atendimentoRepository.findByStatus(status, pageable(numero, tamanho)), numero);
    }

    private Atendimento criarAtendimento(Atendimento atendimento) {
        AtendimentoEntity entity = toEntity(atendimento);
        entity.setId(null);
        entity.getMensagens().add(toMensagemEntity(
                entity, DirecaoMensagemEnum.CLIENTE, atendimento.mensagemCliente(), atendimento.dataProcessamento()
        ));
        return toDomain(atendimentoRepository.save(entity));
    }

    private Atendimento adicionarMensagem(AtendimentoEntity entity, Atendimento recebido) {
        entity.getMensagens().add(toMensagemEntity(
                entity, DirecaoMensagemEnum.CLIENTE, recebido.mensagemCliente(), recebido.dataProcessamento()
        ));
        entity.setMensagemCliente(recebido.mensagemCliente());
        entity.setNomeCliente(recebido.nomeCliente() == null || recebido.nomeCliente().isBlank()
                ? entity.getNomeCliente()
                : recebido.nomeCliente());
        if (entity.getStatus() != StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA) {
            entity.setStatus(StatusAtendimentoEnum.RECEBIDO);
        }
        entity.setDataProcessamento(recebido.dataProcessamento());
        return toDomain(atendimentoRepository.save(entity));
    }

    private PageRequest pageable(int numero, int tamanho) {
        return PageRequest.of(numero, tamanho, Sort.by(
                Sort.Order.desc("dataProcessamento"),
                Sort.Order.desc("id")
        ));
    }

    private Pagina<Atendimento> pagina(Page<AtendimentoEntity> result, int numero) {
        String proximo = result.hasNext() ? token(numero + 1) : null;
        return new Pagina<>(result.stream().map(this::toDomain).toList(), proximo);
    }

    private int numeroPagina(String token) {
        if (token == null || token.isBlank()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8));
            if (value < 0) {
                throw new IllegalArgumentException("Token de paginacao invalido");
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Token de paginacao invalido", exception);
        }
    }

    private String token(int numero) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                Integer.toString(numero).getBytes(StandardCharsets.UTF_8)
        );
    }

    private AtendimentoEntity buscarEntity(String id) {
        return atendimentoRepository.findById(toJpaId(id)).orElseThrow();
    }

    private Long toJpaId(String id) {
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("ID de atendimento invalido para backend JPA", exception);
        }
    }

    private AtendimentoEntity toEntity(Atendimento atendimento) {
        AtendimentoEntity entity = new AtendimentoEntity();
        entity.setId(atendimento.id() == null ? null : toJpaId(atendimento.id()));
        entity.setTelefoneCliente(atendimento.telefoneCliente());
        entity.setNomeCliente(atendimento.nomeCliente());
        entity.setOrigem(atendimento.origem());
        entity.setMensagemCliente(atendimento.mensagemCliente());
        entity.setTipoSolicitacao(atendimento.tipoSolicitacao());
        entity.setCategoria(atendimento.categoria());
        entity.setRespostaGerada(atendimento.respostaGerada());
        entity.setNecessitaAtendimentoHumano(atendimento.necessitaAtendimentoHumano());
        entity.setMotivoEncaminhamento(atendimento.motivoEncaminhamento());
        entity.setConfianca(atendimento.confianca());
        entity.setStatus(atendimento.status());
        entity.setDataProcessamento(atendimento.dataProcessamento());
        return entity;
    }

    private Atendimento toDomain(AtendimentoEntity entity) {
        return new Atendimento(
                entity.getId().toString(), null, entity.getTelefoneCliente(), entity.getNomeCliente(),
                entity.getOrigem(), entity.getMensagemCliente(), entity.getTipoSolicitacao(), entity.getCategoria(),
                entity.getRespostaGerada(), entity.isNecessitaAtendimentoHumano(), entity.getMotivoEncaminhamento(),
                entity.getConfianca(), entity.getStatus(), entity.getDataProcessamento()
        );
    }

    private MensagemAtendimentoEntity toMensagemEntity(
            AtendimentoEntity atendimento,
            DirecaoMensagemEnum direcao,
            String conteudo,
            LocalDateTime dataMensagem
    ) {
        MensagemAtendimentoEntity mensagem = new MensagemAtendimentoEntity();
        mensagem.setAtendimento(atendimento);
        mensagem.setDirecao(direcao);
        mensagem.setConteudo(conteudo);
        mensagem.setDataMensagem(dataMensagem);
        return mensagem;
    }
}
