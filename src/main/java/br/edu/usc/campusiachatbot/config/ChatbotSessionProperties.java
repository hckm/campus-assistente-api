package br.edu.usc.campusiachatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "chatbot.sessao")
public record ChatbotSessionProperties(
        int janelaInatividadeMinutos,
        int maxHistoricoMensagens,
        int processamentoExpiracaoSegundos) {

    @ConstructorBinding
    public ChatbotSessionProperties {
    }

    public ChatbotSessionProperties(int janelaInatividadeMinutos, int maxHistoricoMensagens) {
        this(janelaInatividadeMinutos, maxHistoricoMensagens, 60);
    }
}
