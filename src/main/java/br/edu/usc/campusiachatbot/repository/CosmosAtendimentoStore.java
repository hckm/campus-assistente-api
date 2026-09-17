package br.edu.usc.campusiachatbot.repository;

import br.edu.usc.campusiachatbot.config.CosmosProperties;
import br.edu.usc.campusiachatbot.domain.Atendimento;
import br.edu.usc.campusiachatbot.domain.InicioInteracao;
import br.edu.usc.campusiachatbot.domain.MensagemConversa;
import br.edu.usc.campusiachatbot.domain.Pagina;
import br.edu.usc.campusiachatbot.enums.CategoriaAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.DirecaoMensagemEnum;
import br.edu.usc.campusiachatbot.enums.OrigemMensagemEnum;
import br.edu.usc.campusiachatbot.enums.StatusAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.TipoSolicitacaoEnum;
import br.edu.usc.campusiachatbot.exception.IdempotenciaConflitoException;
import br.edu.usc.campusiachatbot.exception.InteracaoEmAndamentoException;
import br.edu.usc.campusiachatbot.exception.InteracaoFalhouException;
import br.edu.usc.campusiachatbot.store.AtendimentoStore;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosBatch;
import com.azure.cosmos.models.CosmosBatchItemRequestOptions;
import com.azure.cosmos.models.CosmosBatchResponse;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CosmosAtendimentoStore implements AtendimentoStore {

    private static final int SCHEMA_VERSION = 1;
    private static final String TIPO_ATENDIMENTO = "atendimento";
    private static final String TIPO_MENSAGEM = "mensagem";
    private static final String TIPO_SESSAO = "sessao";
    private static final String TIPO_EVENTO = "evento";
    private static final String ESTADO_PROCESSANDO = "PROCESSANDO";
    private static final String ESTADO_CONCLUIDO = "CONCLUIDO";
    private static final String ESTADO_FALHA = "FALHA";

    private final CosmosContainer container;

    public CosmosAtendimentoStore(CosmosClient client, CosmosProperties properties) {
        this.container = client.getDatabase(properties.getDatabase())
                .getContainer(properties.getConversasContainer());
    }

    @Override
    public InicioInteracao iniciarInteracao(
            Atendimento recebido,
            List<StatusAtendimentoEnum> statuses,
            LocalDateTime limite,
            LocalDateTime processamentoExpiraEm,
            String idempotencyKey,
            String payloadHash
    ) {
        String clienteChave = recebido.clienteChave() == null
                ? ClienteChave.criar(recebido.telefoneCliente())
                : recebido.clienteChave();
        if (!clienteChave.equals(ClienteChave.criar(recebido.telefoneCliente()))) {
            throw new IllegalArgumentException("clienteChave diverge do telefoneCliente");
        }
        PartitionKey partitionKey = new PartitionKey(clienteChave);
        String eventoId = idempotencyKey == null
                ? null
                : "evento:" + recebido.origem().name().toLowerCase() + ":" + hash(idempotencyKey);
        String processamentoToken = UUID.randomUUID().toString();

        for (int attempt = 0; attempt < 3; attempt++) {
            Optional<ItemLido> eventoExistente = eventoId == null
                    ? Optional.empty()
                    : ler(eventoId, partitionKey);
            if (eventoExistente.isPresent()) {
                return repetirEvento(eventoExistente.get(), payloadHash, partitionKey, recebido.dataProcessamento());
            }

            Optional<ItemLido> sessaoLida = ler("sessao:ativa", partitionKey);
            ConversaDocument sessaoAtual = sessaoLida.map(ItemLido::document).orElse(null);
            if (processamentoAtivo(sessaoAtual, recebido.dataProcessamento())) {
                throw new InteracaoEmAndamentoException();
            }

            boolean continuar = sessaoElegivel(sessaoAtual, statuses, limite);
            ItemLido resumoLido = continuar
                    ? ler("atendimento:" + sessaoAtual.getAtendimentoId(), partitionKey).orElseThrow()
                    : null;
            String atendimentoId = continuar ? sessaoAtual.getAtendimentoId() : UUID.randomUUID().toString();
            long sequencia = continuar ? sessaoAtual.getProximaSequencia() : 1L;
            ConversaDocument resumo = continuar
                    ? atualizarResumoCliente(resumoLido.document(), recebido)
                    : novoResumo(recebido, clienteChave, atendimentoId);
            ConversaDocument mensagem = mensagem(
                    clienteChave, atendimentoId, sequencia, DirecaoMensagemEnum.CLIENTE,
                    recebido.mensagemCliente(), recebido.dataProcessamento()
            );
            ConversaDocument sessao = sessao(
                    clienteChave, atendimentoId, recebido.dataProcessamento(), sequencia + 1,
                    processamentoToken, processamentoExpiraEm, StatusAtendimentoEnum.valueOf(resumo.getStatus())
            );
            ConversaDocument evento = eventoId == null ? null : evento(
                    eventoId, clienteChave, atendimentoId, idempotencyKey, payloadHash,
                    processamentoToken, processamentoExpiraEm
            );

            CosmosBatch batch = CosmosBatch.createCosmosBatch(partitionKey);
            if (sessaoLida.isPresent()) {
                batch.replaceItemOperation("sessao:ativa", sessao, ifMatch(sessaoLida.get().etag()));
            } else {
                batch.createItemOperation(sessao);
            }
            if (continuar) {
                batch.replaceItemOperation(resumo.getId(), resumo, ifMatch(resumoLido.etag()));
            } else {
                batch.createItemOperation(resumo);
            }
            batch.createItemOperation(mensagem);
            if (evento != null) {
                batch.createItemOperation(evento);
            }

            CosmosBatchResponse response = container.executeCosmosBatch(batch);
            if (response.isSuccessStatusCode()) {
                return new InicioInteracao(toDomain(resumo), processamentoToken, eventoId, false);
            }
            if (!conflito(response)) {
                throw falhaBatch("iniciar", response);
            }
        }
        throw new InteracaoEmAndamentoException();
    }

    @Override
    public Optional<Atendimento> buscarConversaAtiva(
            String telefoneCliente,
            List<StatusAtendimentoEnum> statuses,
            LocalDateTime limite
    ) {
        String clienteChave = ClienteChave.criar(telefoneCliente);
        PartitionKey partitionKey = new PartitionKey(clienteChave);
        Optional<ItemLido> sessao = ler("sessao:ativa", partitionKey);
        if (sessao.isEmpty() || !sessaoElegivel(sessao.get().document(), statuses, limite)) {
            return Optional.empty();
        }
        return ler("atendimento:" + sessao.get().document().getAtendimentoId(), partitionKey)
                .map(ItemLido::document)
                .map(this::toDomain);
    }

    @Override
    public List<MensagemConversa> buscarHistoricoMensagens(Atendimento atendimento, int limite) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tipo = @tipo AND c.atendimentoId = @atendimentoId "
                        + "ORDER BY c.tipo ASC, c.atendimentoId ASC, c.sequencia DESC OFFSET 0 LIMIT @limite",
                List.of(
                        new SqlParameter("@tipo", TIPO_MENSAGEM),
                        new SqlParameter("@atendimentoId", atendimento.id()),
                        new SqlParameter("@limite", limite)
                )
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions()
                .setPartitionKey(new PartitionKey(atendimento.clienteChave()));
        List<MensagemConversa> mensagens = new ArrayList<>();
        container.queryItems(query, options, ConversaDocument.class).iterableByPage()
                .forEach(page -> page.getResults().forEach(document -> mensagens.add(new MensagemConversa(
                        DirecaoMensagemEnum.valueOf(document.getDirecao()), document.getConteudo()
                ))));
        Collections.reverse(mensagens);
        return List.copyOf(mensagens);
    }

    @Override
    public Atendimento registrarRespostaComMensagemBot(InicioInteracao interacao, Atendimento atualizado) {
        PartitionKey partitionKey = new PartitionKey(interacao.atendimento().clienteChave());
        ItemLido sessaoLida = ler("sessao:ativa", partitionKey).orElseThrow();
        validarToken(sessaoLida.document(), interacao.processamentoToken());
        ItemLido resumoLido = ler("atendimento:" + atualizado.id(), partitionKey).orElseThrow();
        long sequencia = sessaoLida.document().getProximaSequencia();
        ConversaDocument resumo = resumo(atualizado);
        ConversaDocument sessao = sessao(
                atualizado.clienteChave(), atualizado.id(), atualizado.dataProcessamento(), sequencia + 1,
                null, null, atualizado.status()
        );
        ConversaDocument mensagem = mensagem(
                atualizado.clienteChave(), atualizado.id(), sequencia, DirecaoMensagemEnum.BOT,
                atualizado.respostaGerada(), atualizado.dataProcessamento()
        );
        Optional<ItemLido> eventoLido = interacao.eventoId() == null
                ? Optional.empty()
                : ler(interacao.eventoId(), partitionKey);

        CosmosBatch batch = CosmosBatch.createCosmosBatch(partitionKey);
        batch.replaceItemOperation(sessao.getId(), sessao, ifMatch(sessaoLida.etag()));
        batch.replaceItemOperation(resumo.getId(), resumo, ifMatch(resumoLido.etag()));
        batch.createItemOperation(mensagem);
        eventoLido.ifPresent(item -> {
            ConversaDocument evento = item.document();
            evento.setEstadoEvento(ESTADO_CONCLUIDO);
            evento.setResultado(resumo);
            evento.setProcessamentoToken(null);
            evento.setProcessamentoExpiraEm(null);
            batch.replaceItemOperation(evento.getId(), evento, ifMatch(item.etag()));
        });
        CosmosBatchResponse response = container.executeCosmosBatch(batch);
        if (!response.isSuccessStatusCode()) {
            if (conflito(response)) {
                throw new InteracaoEmAndamentoException();
            }
            throw falhaBatch("concluir", response);
        }
        return toDomain(resumo);
    }

    @Override
    public void marcarErro(InicioInteracao interacao, LocalDateTime dataProcessamento) {
        if (interacao.processamentoToken() == null) {
            throw new IllegalStateException("Interacao Cosmos sem token de processamento");
        }
        PartitionKey partitionKey = new PartitionKey(interacao.atendimento().clienteChave());
        Optional<ItemLido> sessaoLida = ler("sessao:ativa", partitionKey);
        if (sessaoLida.isEmpty()
                || !interacao.processamentoToken().equals(sessaoLida.get().document().getProcessamentoToken())) {
            return;
        }
        ItemLido resumoLido = ler("atendimento:" + interacao.atendimento().id(), partitionKey).orElseThrow();
        ConversaDocument resumo = resumoLido.document();
        resumo.setStatus(StatusAtendimentoEnum.ERRO_PROCESSAMENTO.name());
        resumo.setDataProcessamento(dataProcessamento.toString());
        ConversaDocument sessao = sessao(
                interacao.atendimento().clienteChave(), interacao.atendimento().id(), dataProcessamento,
                sessaoLida.get().document().getProximaSequencia(), null, null,
                StatusAtendimentoEnum.ERRO_PROCESSAMENTO
        );
        Optional<ItemLido> eventoLido = interacao.eventoId() == null
                ? Optional.empty()
                : ler(interacao.eventoId(), partitionKey);
        CosmosBatch batch = CosmosBatch.createCosmosBatch(partitionKey);
        batch.replaceItemOperation(sessao.getId(), sessao, ifMatch(sessaoLida.get().etag()));
        batch.replaceItemOperation(resumo.getId(), resumo, ifMatch(resumoLido.etag()));
        eventoLido.ifPresent(item -> {
            ConversaDocument evento = item.document();
            if (interacao.processamentoToken().equals(evento.getProcessamentoToken())) {
                evento.setEstadoEvento(ESTADO_FALHA);
                evento.setProcessamentoToken(null);
                evento.setProcessamentoExpiraEm(null);
                batch.replaceItemOperation(evento.getId(), evento, ifMatch(item.etag()));
            }
        });
        CosmosBatchResponse response = container.executeCosmosBatch(batch);
        if (!response.isSuccessStatusCode() && !conflito(response)) {
            throw falhaBatch("marcar erro", response);
        }
    }

    @Override
    public Pagina<Atendimento> listarTodosOrdenadosPorDataProcessamentoDesc(String token, int tamanho) {
        return consultarPagina(
                "SELECT TOP @limite * FROM c WHERE c.tipo = @tipo",
                "SELECT TOP @limite * FROM c WHERE c.tipo = @tipo "
                        + "AND (c.dataProcessamento < @data OR "
                        + "(c.dataProcessamento = @data AND c.atendimentoId < @id))",
                " ORDER BY c.tipo ASC, c.dataProcessamento DESC, c.atendimentoId DESC",
                List.of(), token, tamanho
        );
    }

    @Override
    public Optional<Atendimento> buscarPorId(String id) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tipo = @tipo AND c.atendimentoId = @atendimentoId",
                List.of(new SqlParameter("@tipo", TIPO_ATENDIMENTO), new SqlParameter("@atendimentoId", id))
        );
        for (var page : container.queryItems(query, new CosmosQueryRequestOptions(), ConversaDocument.class)
                .iterableByPage()) {
            if (!page.getResults().isEmpty()) {
                return Optional.of(toDomain(page.getResults().getFirst()));
            }
        }
        return Optional.empty();
    }

    @Override
    public Pagina<Atendimento> listarPorStatusOrdenadosPorDataProcessamentoDesc(
            StatusAtendimentoEnum status,
            String token,
            int tamanho
    ) {
        return consultarPagina(
                "SELECT TOP @limite * FROM c WHERE c.tipo = @tipo AND c.status = @status",
                "SELECT TOP @limite * FROM c WHERE c.tipo = @tipo AND c.status = @status "
                        + "AND (c.dataProcessamento < @data OR "
                        + "(c.dataProcessamento = @data AND c.atendimentoId < @id))",
                " ORDER BY c.tipo ASC, c.status ASC, c.dataProcessamento DESC, c.atendimentoId DESC",
                List.of(new SqlParameter("@status", status.name())), token, tamanho
        );
    }

    private Pagina<Atendimento> consultarPagina(
            String primeiraPaginaSql,
            String proximaPaginaSql,
            String orderBy,
            List<SqlParameter> parameters,
            String token,
            int tamanho
    ) {
        MarcadorPagina marcador = decodificarMarcador(token);
        List<SqlParameter> all = new ArrayList<>();
        all.add(new SqlParameter("@tipo", TIPO_ATENDIMENTO));
        all.add(new SqlParameter("@limite", tamanho + 1));
        all.addAll(parameters);
        if (marcador != null) {
            all.add(new SqlParameter("@data", marcador.dataProcessamento()));
            all.add(new SqlParameter("@id", marcador.atendimentoId()));
        }
        String sql = (marcador == null ? primeiraPaginaSql : proximaPaginaSql) + orderBy;
        List<ConversaDocument> documentos = new ArrayList<>();
        container.queryItems(
                new SqlQuerySpec(sql, all), new CosmosQueryRequestOptions(), ConversaDocument.class
        ).iterableByPage().forEach(page -> documentos.addAll(page.getResults()));
        if (documentos.isEmpty()) {
            return new Pagina<>(List.of(), null);
        }
        boolean temProxima = documentos.size() > tamanho;
        List<ConversaDocument> pagina = documentos.subList(0, Math.min(tamanho, documentos.size()));
        ConversaDocument ultimo = pagina.getLast();
        return new Pagina<>(
                pagina.stream().map(this::toDomain).toList(),
                temProxima ? codificarMarcador(ultimo) : null
        );
    }

    private InicioInteracao repetirEvento(
            ItemLido eventoLido,
            String payloadHash,
            PartitionKey partitionKey,
            LocalDateTime agora
    ) {
        ConversaDocument evento = eventoLido.document();
        if (!payloadHash.equals(evento.getPayloadHash())) {
            throw new IdempotenciaConflitoException();
        }
        if (ESTADO_CONCLUIDO.equals(evento.getEstadoEvento()) && evento.getResultado() != null) {
            return new InicioInteracao(toDomain(evento.getResultado()), null, evento.getId(), true);
        }
        if (ESTADO_FALHA.equals(evento.getEstadoEvento())) {
            throw new InteracaoFalhouException();
        }
        if (!ESTADO_PROCESSANDO.equals(evento.getEstadoEvento()) || evento.getProcessamentoExpiraEm() == null) {
            throw new IllegalStateException("Evento de idempotencia invalido no Cosmos");
        }
        if (!LocalDateTime.parse(evento.getProcessamentoExpiraEm()).isAfter(agora)) {
            return encerrarEventoExpirado(eventoLido, payloadHash, partitionKey, agora);
        }
        throw new InteracaoEmAndamentoException();
    }

    private InicioInteracao encerrarEventoExpirado(
            ItemLido eventoInicial,
            String payloadHash,
            PartitionKey partitionKey,
            LocalDateTime agora
    ) {
        ItemLido eventoLido = eventoInicial;
        for (int attempt = 0; attempt < 3; attempt++) {
            ConversaDocument evento = eventoLido.document();
            if (!payloadHash.equals(evento.getPayloadHash())) {
                throw new IdempotenciaConflitoException();
            }
            if (ESTADO_CONCLUIDO.equals(evento.getEstadoEvento()) && evento.getResultado() != null) {
                return new InicioInteracao(toDomain(evento.getResultado()), null, evento.getId(), true);
            }
            if (ESTADO_FALHA.equals(evento.getEstadoEvento())) {
                throw new InteracaoFalhouException();
            }
            if (!ESTADO_PROCESSANDO.equals(evento.getEstadoEvento())
                    || evento.getProcessamentoExpiraEm() == null) {
                throw new IllegalStateException("Evento de idempotencia invalido no Cosmos");
            }
            if (LocalDateTime.parse(evento.getProcessamentoExpiraEm()).isAfter(agora)) {
                throw new InteracaoEmAndamentoException();
            }

            Optional<ItemLido> sessaoLida = ler("sessao:ativa", partitionKey);
            boolean sessaoDoEvento = sessaoLida.isPresent()
                    && evento.getProcessamentoToken() != null
                    && evento.getProcessamentoToken().equals(
                    sessaoLida.get().document().getProcessamentoToken()
            );
            ItemLido resumoLido = sessaoDoEvento
                    ? ler("atendimento:" + evento.getAtendimentoId(), partitionKey).orElseThrow()
                    : null;

            evento.setEstadoEvento(ESTADO_FALHA);
            evento.setProcessamentoToken(null);
            evento.setProcessamentoExpiraEm(null);
            CosmosBatch batch = CosmosBatch.createCosmosBatch(partitionKey);
            batch.replaceItemOperation(evento.getId(), evento, ifMatch(eventoLido.etag()));
            if (sessaoDoEvento) {
                ConversaDocument resumo = resumoLido.document();
                resumo.setStatus(StatusAtendimentoEnum.ERRO_PROCESSAMENTO.name());
                resumo.setDataProcessamento(agora.toString());
                ConversaDocument sessao = sessao(
                        resumo.getClienteChave(), resumo.getAtendimentoId(), agora,
                        sessaoLida.get().document().getProximaSequencia(), null, null,
                        StatusAtendimentoEnum.ERRO_PROCESSAMENTO
                );
                batch.replaceItemOperation(sessao.getId(), sessao, ifMatch(sessaoLida.get().etag()));
                batch.replaceItemOperation(resumo.getId(), resumo, ifMatch(resumoLido.etag()));
            }

            CosmosBatchResponse response = container.executeCosmosBatch(batch);
            if (response.isSuccessStatusCode()) {
                throw new InteracaoFalhouException();
            }
            if (!conflito(response)) {
                throw falhaBatch("encerrar evento expirado", response);
            }
            eventoLido = ler(evento.getId(), partitionKey).orElseThrow();
        }
        throw new InteracaoEmAndamentoException();
    }

    private boolean processamentoAtivo(ConversaDocument sessao, LocalDateTime agora) {
        return sessao != null
                && sessao.getProcessamentoToken() != null
                && sessao.getProcessamentoExpiraEm() != null
                && LocalDateTime.parse(sessao.getProcessamentoExpiraEm()).isAfter(agora);
    }

    private boolean sessaoElegivel(
            ConversaDocument sessao,
            List<StatusAtendimentoEnum> statuses,
            LocalDateTime limite
    ) {
        if (sessao == null || sessao.getStatus() == null || sessao.getUltimaAtividade() == null) {
            return false;
        }
        StatusAtendimentoEnum status = StatusAtendimentoEnum.valueOf(sessao.getStatus());
        return statuses.contains(status) && LocalDateTime.parse(sessao.getUltimaAtividade()).isAfter(limite);
    }

    private Optional<ItemLido> ler(String id, PartitionKey partitionKey) {
        try {
            CosmosItemResponse<ConversaDocument> response = container.readItem(id, partitionKey, ConversaDocument.class);
            return Optional.of(new ItemLido(response.getItem(), response.getETag()));
        } catch (CosmosException exception) {
            if (exception.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private CosmosBatchItemRequestOptions ifMatch(String etag) {
        return new CosmosBatchItemRequestOptions().setIfMatchETag(etag);
    }

    private boolean conflito(CosmosBatchResponse response) {
        return response.getStatusCode() == 409
                || response.getStatusCode() == 412
                || response.getResults().stream().anyMatch(result ->
                result.getStatusCode() == 409 || result.getStatusCode() == 412);
    }

    private IllegalStateException falhaBatch(String operation, CosmosBatchResponse response) {
        return new IllegalStateException(
                "Falha ao " + operation + " atendimento no batch Cosmos; status=" + response.getStatusCode()
        );
    }

    private ConversaDocument novoResumo(Atendimento recebido, String clienteChave, String atendimentoId) {
        return resumo(new Atendimento(
                atendimentoId, clienteChave, recebido.telefoneCliente(), recebido.nomeCliente(), recebido.origem(),
                recebido.mensagemCliente(), null, null, null, false, null, null,
                StatusAtendimentoEnum.RECEBIDO, recebido.dataProcessamento()
        ));
    }

    private ConversaDocument atualizarResumoCliente(ConversaDocument resumo, Atendimento recebido) {
        resumo.setMensagemCliente(recebido.mensagemCliente());
        if (recebido.nomeCliente() != null && !recebido.nomeCliente().isBlank()) {
            resumo.setNomeCliente(recebido.nomeCliente());
        }
        if (!StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA.name().equals(resumo.getStatus())) {
            resumo.setStatus(StatusAtendimentoEnum.RECEBIDO.name());
        }
        resumo.setDataProcessamento(recebido.dataProcessamento().toString());
        return resumo;
    }

    private ConversaDocument resumo(Atendimento atendimento) {
        ConversaDocument document = base("atendimento:" + atendimento.id(), atendimento.clienteChave(), TIPO_ATENDIMENTO);
        document.setAtendimentoId(atendimento.id());
        document.setTelefoneCliente(atendimento.telefoneCliente());
        document.setNomeCliente(atendimento.nomeCliente());
        document.setOrigem(atendimento.origem().name());
        document.setMensagemCliente(atendimento.mensagemCliente());
        document.setTipoSolicitacao(nome(atendimento.tipoSolicitacao()));
        document.setCategoria(nome(atendimento.categoria()));
        document.setRespostaGerada(atendimento.respostaGerada());
        document.setNecessitaAtendimentoHumano(atendimento.necessitaAtendimentoHumano());
        document.setMotivoEncaminhamento(atendimento.motivoEncaminhamento());
        document.setConfianca(atendimento.confianca());
        document.setStatus(atendimento.status().name());
        document.setDataProcessamento(atendimento.dataProcessamento().toString());
        return document;
    }

    private ConversaDocument mensagem(
            String clienteChave,
            String atendimentoId,
            long sequencia,
            DirecaoMensagemEnum direcao,
            String conteudo,
            LocalDateTime data
    ) {
        String suffix = String.format("%020d", sequencia);
        ConversaDocument document = base(
                "mensagem:" + atendimentoId + ":" + suffix, clienteChave, TIPO_MENSAGEM
        );
        document.setAtendimentoId(atendimentoId);
        document.setSequencia(sequencia);
        document.setDirecao(direcao.name());
        document.setConteudo(conteudo);
        document.setDataProcessamento(data.toString());
        return document;
    }

    private ConversaDocument sessao(
            String clienteChave,
            String atendimentoId,
            LocalDateTime atividade,
            long proximaSequencia,
            String token,
            LocalDateTime expiraEm,
            StatusAtendimentoEnum status
    ) {
        ConversaDocument document = base("sessao:ativa", clienteChave, TIPO_SESSAO);
        document.setAtendimentoId(atendimentoId);
        document.setUltimaAtividade(atividade.toString());
        document.setProximaSequencia(proximaSequencia);
        document.setProcessamentoToken(token);
        document.setProcessamentoExpiraEm(expiraEm == null ? null : expiraEm.toString());
        document.setStatus(status.name());
        return document;
    }

    private ConversaDocument evento(
            String id,
            String clienteChave,
            String atendimentoId,
            String idempotencyKey,
            String payloadHash,
            String token,
            LocalDateTime expiraEm
    ) {
        ConversaDocument document = base(id, clienteChave, TIPO_EVENTO);
        document.setAtendimentoId(atendimentoId);
        document.setIdempotencyKeyHash(hash(idempotencyKey));
        document.setPayloadHash(payloadHash);
        document.setEstadoEvento(ESTADO_PROCESSANDO);
        document.setProcessamentoToken(token);
        document.setProcessamentoExpiraEm(expiraEm.toString());
        return document;
    }

    private ConversaDocument base(String id, String clienteChave, String tipo) {
        ConversaDocument document = new ConversaDocument();
        document.setId(id);
        document.setClienteChave(clienteChave);
        document.setTipo(tipo);
        document.setSchemaVersion(SCHEMA_VERSION);
        return document;
    }

    private Atendimento toDomain(ConversaDocument document) {
        if (document == null
                || !TIPO_ATENDIMENTO.equals(document.getTipo())
                || document.getSchemaVersion() != SCHEMA_VERSION
                || document.getAtendimentoId() == null
                || !document.getId().equals("atendimento:" + document.getAtendimentoId())
                || document.getClienteChave() == null
                || document.getTelefoneCliente() == null
                || document.getOrigem() == null
                || document.getMensagemCliente() == null
                || document.getStatus() == null
                || document.getDataProcessamento() == null) {
            throw new IllegalStateException("Documento de atendimento invalido no Cosmos");
        }
        return new Atendimento(
                document.getAtendimentoId(), document.getClienteChave(), document.getTelefoneCliente(),
                document.getNomeCliente(), OrigemMensagemEnum.valueOf(document.getOrigem()),
                document.getMensagemCliente(), enumValue(TipoSolicitacaoEnum.class, document.getTipoSolicitacao()),
                enumValue(CategoriaAtendimentoEnum.class, document.getCategoria()), document.getRespostaGerada(),
                document.isNecessitaAtendimentoHumano(), document.getMotivoEncaminhamento(),
                document.getConfianca(), StatusAtendimentoEnum.valueOf(document.getStatus()),
                LocalDateTime.parse(document.getDataProcessamento())
        );
    }

    private void validarToken(ConversaDocument sessao, String token) {
        if (token == null || !token.equals(sessao.getProcessamentoToken())) {
            throw new InteracaoEmAndamentoException();
        }
    }

    private String codificarMarcador(ConversaDocument document) {
        String value = document.getDataProcessamento() + "\u001f" + document.getAtendimentoId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private MarcadorPagina decodificarMarcador(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            if (token.length() > 2048) {
                throw new IllegalArgumentException("Token de paginacao invalido");
            }
            String value = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = value.split("\u001f", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalArgumentException("Token de paginacao invalido");
            }
            LocalDateTime.parse(parts[0]);
            return new MarcadorPagina(parts[0], parts[1]);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Token de paginacao invalido", exception);
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponivel", exception);
        }
    }

    private String nome(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private record ItemLido(ConversaDocument document, String etag) {
    }

    private record MarcadorPagina(String dataProcessamento, String atendimentoId) {
    }
}
