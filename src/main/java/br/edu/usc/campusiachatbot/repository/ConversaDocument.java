package br.edu.usc.campusiachatbot.repository;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConversaDocument {

    private String id;
    private String clienteChave;
    private String tipo;
    private int schemaVersion;
    private String atendimentoId;
    private String legacyId;
    private String telefoneCliente;
    private String nomeCliente;
    private String origem;
    private String mensagemCliente;
    private String tipoSolicitacao;
    private String categoria;
    private String respostaGerada;
    private boolean necessitaAtendimentoHumano;
    private String motivoEncaminhamento;
    private Double confianca;
    private String status;
    private String dataProcessamento;
    private Long sequencia;
    private String direcao;
    private String conteudo;
    private String ultimaAtividade;
    private Long proximaSequencia;
    private String processamentoToken;
    private String processamentoExpiraEm;
    private String idempotencyKeyHash;
    private String payloadHash;
    private String estadoEvento;
    private ConversaDocument resultado;

    @JsonProperty("_etag")
    private String etag;
}
