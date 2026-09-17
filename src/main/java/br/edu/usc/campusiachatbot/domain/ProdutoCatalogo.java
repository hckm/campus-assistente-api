package br.edu.usc.campusiachatbot.domain;

import java.math.BigDecimal;

public record ProdutoCatalogo(
        String id,
        String legacyId,
        Integer codigoCatalogo,
        String categoria,
        String produto,
        String descricao,
        BigDecimal precoAtual,
        BigDecimal precoOriginal,
        String urlCatalogo
) {
}
