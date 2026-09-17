package br.edu.usc.campusiachatbot.service;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface InferenciaIa {

    String executar(List<Map<String, Object>> contents);
}
