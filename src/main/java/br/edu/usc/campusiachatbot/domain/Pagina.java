package br.edu.usc.campusiachatbot.domain;

import java.util.List;

public record Pagina<T>(List<T> itens, String proximoToken) {
}
