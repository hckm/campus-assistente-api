package br.edu.usc.campusiachatbot.domain;

import java.util.List;

public record EstabelecimentoComercial(
        String estabelecimentoId,
        String nome,
        String tipo,
        String horarioFuncionamento,
        String endereco,
        String formasPagamento,
        String condicoesParcelamento,
        String entrega,
        List<String> cidadesAtendidas,
        String uf,
        String telefone,
        String site
) {
    public EstabelecimentoComercial {
        cidadesAtendidas = cidadesAtendidas == null ? List.of() : List.copyOf(cidadesAtendidas);
    }
}
