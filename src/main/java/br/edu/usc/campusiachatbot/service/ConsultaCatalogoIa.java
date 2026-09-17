package br.edu.usc.campusiachatbot.service;

import java.math.BigDecimal;

public record ConsultaCatalogoIa(
        ConsultaCatalogoOperacao operacao,
        String termo,
        String categoria,
        BigDecimal precoMinimo,
        BigDecimal precoMaximo
) {
}
