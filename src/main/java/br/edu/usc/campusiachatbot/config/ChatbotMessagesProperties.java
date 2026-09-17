package br.edu.usc.campusiachatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "chatbot.mensagens")
public record ChatbotMessagesProperties(
        String horarioInformado,
        String horarioNaoConfigurado,
        String pagamentoInformado,
        String pagamentoNaoConfigurado,
        String enderecoInformado,
        String enderecoNaoConfigurado,
        String entregaGenerica,
        String entregaCidadeAtendida,
        String entregaForaArea,
        String entregaSolicitarCidade,
        String entregaCepNaoValidado
) {
    public String horarioInformado(String nome, String horario) {
        return preencher(horarioInformado, Map.of("nome", nome, "horario", horario));
    }

    public String pagamentoInformado(String nome, String formasPagamento) {
        return preencher(pagamentoInformado, Map.of("nome", nome, "formasPagamento", formasPagamento));
    }

    public String enderecoInformado(String nome, String endereco) {
        return preencher(enderecoInformado, Map.of("nome", nome, "endereco", endereco));
    }

    public String entregaCidadeAtendida(String cidade) {
        return preencher(entregaCidadeAtendida, Map.of("cidade", cidade));
    }

    public String entregaForaArea(String cidades) {
        return preencher(entregaForaArea, Map.of("cidades", cidades));
    }

    public String entregaSolicitarCidade(String cidades) {
        return preencher(entregaSolicitarCidade, Map.of("cidades", cidades));
    }

    public String entregaCepNaoValidado() {
        return entregaCepNaoValidado;
    }

    private String preencher(String template, Map<String, String> valores) {
        if (template == null || template.isBlank()) {
            return "";
        }

        String resultado = template;
        for (Map.Entry<String, String> entrada : valores.entrySet()) {
            resultado = resultado.replace("{" + entrada.getKey() + "}", entrada.getValue());
        }
        return resultado;
    }
}
