package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.config.CosmosProperties;
import br.edu.usc.campusiachatbot.config.EstabelecimentoProperties;
import br.edu.usc.campusiachatbot.domain.EstabelecimentoComercial;
import br.edu.usc.campusiachatbot.store.EstabelecimentoComercialStore;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "persistencia", name = "backend", havingValue = "COSMOS")
public class CosmosEstabelecimentoComercialProvider implements EstabelecimentoComercialProvider {

    private final EstabelecimentoComercialStore store;
    private final CosmosProperties properties;
    private volatile EstabelecimentoProperties cache;

    @Override
    public EstabelecimentoProperties obter() {
        EstabelecimentoProperties estabelecimento = cache;
        if (estabelecimento != null) {
            return estabelecimento;
        }
        synchronized (this) {
            if (cache == null) {
                EstabelecimentoComercial carregado = store.buscar(properties.getEstabelecimentoId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Estabelecimento comercial nao encontrado no Cosmos: "
                                        + properties.getEstabelecimentoId()
                        ));
                cache = toProperties(carregado);
            }
            return cache;
        }
    }

    private EstabelecimentoProperties toProperties(EstabelecimentoComercial estabelecimento) {
        return new EstabelecimentoProperties(
                estabelecimento.nome(),
                estabelecimento.tipo(),
                estabelecimento.horarioFuncionamento(),
                estabelecimento.endereco(),
                estabelecimento.formasPagamento(),
                estabelecimento.condicoesParcelamento(),
                estabelecimento.entrega(),
                estabelecimento.cidadesAtendidas(),
                estabelecimento.uf(),
                estabelecimento.telefone(),
                estabelecimento.site()
        );
    }
}
