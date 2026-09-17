package br.edu.usc.campusiachatbot.dto;

import java.util.List;

public record PaginaResponseDTO<T>(List<T> itens, String proximoToken) {
}
