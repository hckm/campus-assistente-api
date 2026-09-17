package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.domain.MensagemConversa;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.EnderecoEnriquecidoDTO;
import br.edu.usc.campusiachatbot.dto.InterpretacaoIaResponseDTO;

import java.util.List;

public interface InterpretacaoIaService {

    InterpretacaoIaResponseDTO interpretarMensagem(
            ChatbotRequestDTO request,
            EnderecoEnriquecidoDTO enderecoEnriquecido,
            List<MensagemConversa> historico);
}
