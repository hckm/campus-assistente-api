package br.edu.usc.campusiachatbot.config;

import br.edu.usc.campusiachatbot.store.CatalogoStore;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catalogo.consulta-ia")
public record CatalogoConsultaProperties(
        Integer limiteItens,
        Integer maxCaracteresContexto,
        Integer maxBytesContexto
) {

    private static final int LIMITE_ITENS_PADRAO = 8;
    private static final int MAX_CARACTERES_PADRAO = 6000;
    private static final int MAX_BYTES_PADRAO = 12000;
    private static final int MAX_CARACTERES_ABSOLUTO = 20000;
    private static final int MAX_BYTES_ABSOLUTO = 40000;

    public int limiteItensResolvido() {
        int valor = limiteItens == null ? LIMITE_ITENS_PADRAO : limiteItens;
        return Math.min(Math.max(valor, 1), CatalogoStore.LIMITE_MAXIMO_CONSULTA_IA);
    }

    public int maxCaracteresContextoResolvido() {
        int valor = maxCaracteresContexto == null ? MAX_CARACTERES_PADRAO : maxCaracteresContexto;
        return Math.min(Math.max(valor, 1000), MAX_CARACTERES_ABSOLUTO);
    }

    public int maxBytesContextoResolvido() {
        int valor = maxBytesContexto == null ? MAX_BYTES_PADRAO : maxBytesContexto;
        return Math.min(Math.max(valor, 2000), MAX_BYTES_ABSOLUTO);
    }
}
