package br.edu.usc.campusiachatbot.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class CatalogoCategoriaMatcher {

    private CatalogoCategoriaMatcher() {
    }

    static Optional<String> resolver(String termo, List<String> categorias) {
        if (categorias == null) {
            return Optional.empty();
        }
        return categorias.stream()
                .filter(categoria -> corresponde(termo, categoria))
                .findFirst();
    }

    private static boolean corresponde(String termo, String categoria) {
        String termoNormalizado = normalizar(termo);
        String categoriaNormalizada = normalizar(categoria);
        if (termoNormalizado.isBlank() || categoriaNormalizada.isBlank()) {
            return false;
        }
        if (termoNormalizado.equals(categoriaNormalizada)
                || termoNormalizado.contains(categoriaNormalizada)
                || categoriaNormalizada.contains(termoNormalizado)) {
            return true;
        }
        if (intencaoEmagrecimento(termoNormalizado) && categoriaNormalizada.contains("emagrec")) {
            return true;
        }
        for (String palavraTermo : termoNormalizado.split(" ")) {
            for (String palavraCategoria : categoriaNormalizada.split(" ")) {
                if (prefixoComum(palavraTermo, palavraCategoria) >= 7) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean intencaoEmagrecimento(String termo) {
        return termo.contains("emagrec")
                || termo.contains("perder peso")
                || termo.contains("perda peso")
                || termo.contains("reduzir peso");
    }

    private static int prefixoComum(String primeiro, String segundo) {
        int limite = Math.min(primeiro.length(), segundo.length());
        int indice = 0;
        while (indice < limite && primeiro.charAt(indice) == segundo.charAt(indice)) {
            indice++;
        }
        return indice;
    }

    private static String normalizar(String valor) {
        return Normalizer.normalize(valor == null ? "" : valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
