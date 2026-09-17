package br.edu.usc.campusiachatbot.store;

import br.edu.usc.campusiachatbot.domain.EstabelecimentoComercial;

import java.util.Optional;

public interface EstabelecimentoComercialStore {

    Optional<EstabelecimentoComercial> buscar(String estabelecimentoId);

    boolean criarSeAusente(EstabelecimentoComercial estabelecimento);
}
