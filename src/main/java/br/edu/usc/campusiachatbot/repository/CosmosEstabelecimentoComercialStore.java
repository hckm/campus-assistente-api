package br.edu.usc.campusiachatbot.repository;

import br.edu.usc.campusiachatbot.config.CosmosProperties;
import br.edu.usc.campusiachatbot.domain.EstabelecimentoComercial;
import br.edu.usc.campusiachatbot.store.EstabelecimentoComercialStore;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.PartitionKey;

import java.util.List;
import java.util.Optional;

public class CosmosEstabelecimentoComercialStore implements EstabelecimentoComercialStore {

    private static final String TIPO = "estabelecimento";
    private static final int SCHEMA_VERSION = 1;

    private final CosmosContainer container;

    public CosmosEstabelecimentoComercialStore(CosmosClient client, CosmosProperties properties) {
        this.container = client.getDatabase(properties.getDatabase())
                .getContainer(properties.getEstabelecimentosContainer());
    }

    @Override
    public Optional<EstabelecimentoComercial> buscar(String estabelecimentoId) {
        String id = validarId(estabelecimentoId);
        try {
            EstabelecimentoComercialDocument document = container.readItem(
                    id,
                    new PartitionKey(estabelecimentoId),
                    EstabelecimentoComercialDocument.class
            ).getItem();
            return Optional.of(toDomain(document));
        } catch (CosmosException exception) {
            if (exception.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    @Override
    public boolean criarSeAusente(EstabelecimentoComercial estabelecimento) {
        EstabelecimentoComercialDocument document = toDocument(estabelecimento);
        try {
            container.createItem(document, new PartitionKey(document.getEstabelecimentoId()), new CosmosItemRequestOptions());
            return true;
        } catch (CosmosException exception) {
            if (exception.getStatusCode() == 409) {
                return false;
            }
            throw exception;
        }
    }

    private EstabelecimentoComercialDocument toDocument(EstabelecimentoComercial estabelecimento) {
        String estabelecimentoId = validarId(estabelecimento.estabelecimentoId());
        EstabelecimentoComercialDocument document = new EstabelecimentoComercialDocument();
        document.setId(estabelecimentoId);
        document.setEstabelecimentoId(estabelecimentoId);
        document.setTipo(TIPO);
        document.setSchemaVersion(SCHEMA_VERSION);
        document.setNome(texto(estabelecimento.nome()));
        document.setSegmento(texto(estabelecimento.tipo()));
        document.setHorarioFuncionamento(texto(estabelecimento.horarioFuncionamento()));
        document.setEndereco(texto(estabelecimento.endereco()));
        document.setFormasPagamento(texto(estabelecimento.formasPagamento()));
        document.setCondicoesParcelamento(texto(estabelecimento.condicoesParcelamento()));
        document.setEntrega(texto(estabelecimento.entrega()));
        document.setCidadesAtendidas(estabelecimento.cidadesAtendidas() == null ? List.of() : List.copyOf(estabelecimento.cidadesAtendidas()));
        document.setUf(texto(estabelecimento.uf()));
        document.setTelefone(texto(estabelecimento.telefone()));
        document.setSite(texto(estabelecimento.site()));
        return document;
    }

    private EstabelecimentoComercial toDomain(EstabelecimentoComercialDocument document) {
        if (document == null || !TIPO.equals(document.getTipo()) || document.getSchemaVersion() != SCHEMA_VERSION
                || document.getEstabelecimentoId() == null
                || !document.getEstabelecimentoId().equals(document.getId())) {
            throw new IllegalStateException("Documento de estabelecimento invalido no Cosmos");
        }
        return new EstabelecimentoComercial(
                document.getEstabelecimentoId(),
                document.getNome(),
                document.getSegmento(),
                document.getHorarioFuncionamento(),
                document.getEndereco(),
                document.getFormasPagamento(),
                document.getCondicoesParcelamento(),
                document.getEntrega(),
                document.getCidadesAtendidas(),
                document.getUf(),
                document.getTelefone(),
                document.getSite()
        );
    }

    private String validarId(String estabelecimentoId) {
        if (estabelecimentoId == null || !estabelecimentoId.matches("[A-Za-z0-9._-]{1,100}")) {
            throw new IllegalArgumentException("estabelecimentoId invalido");
        }
        return estabelecimentoId;
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
