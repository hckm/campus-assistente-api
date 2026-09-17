package br.edu.usc.campusiachatbot.service;

import java.util.List;
import java.util.Map;

public record ResultadoContextoCatalogo(
        List<Map<String, Object>> contents,
        int produtosSerializados
) {

    public ResultadoContextoCatalogo {
        contents = List.copyOf(contents);
        if (produtosSerializados < 0) {
            throw new IllegalArgumentException("Quantidade de produtos serializados invalida");
        }
    }

    public boolean possuiProdutos() {
        return produtosSerializados > 0;
    }
}
