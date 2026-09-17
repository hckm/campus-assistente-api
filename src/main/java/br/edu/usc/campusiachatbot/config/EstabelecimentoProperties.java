package br.edu.usc.campusiachatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "estabelecimento")
public record EstabelecimentoProperties(
        String nome,
        String tipo,
        String horarioFuncionamento,
        String endereco,
        String formasPagamento,
        String condicoesParcelamento,
        String entrega,
        List<String> cidadesAtendidas,
        String uf,
        String telefone,
        String site
) {
    public String nomeOuPadrao() {
        return textoOuPadrao(nome, "a farmacia");
    }

    public String tipoOuPadrao() {
        return textoOuPadrao(tipo, "farmacia de manipulacao");
    }

    public boolean hasHorarioFuncionamento() {
        return temTexto(horarioFuncionamento);
    }

    public boolean hasFormasPagamento() {
        return temTexto(formasPagamento);
    }

    public boolean hasCondicoesParcelamento() {
        return temTexto(condicoesParcelamento);
    }

    public boolean hasEndereco() {
        return temTexto(endereco);
    }

    public boolean hasEntrega() {
        return temTexto(entrega);
    }

    public boolean hasEntregaCustomizada() {
        return hasEntrega() && !normalizar(entrega).equals(normalizar("Entregamos apenas nas cidades atendidas configuradas."));
    }

    public boolean hasCidadesAtendidas() {
        return cidadesAtendidas != null && cidadesAtendidas.stream().anyMatch(this::temTexto);
    }

    public boolean hasTelefone() {
        return temTexto(telefone);
    }

    public boolean hasSite() {
        return temTexto(site);
    }

    public boolean hasUf() {
        return temTexto(uf);
    }

    public String horarioFuncionamentoOuNaoInformado() {
        return textoOuPadrao(horarioFuncionamento, "nao informado");
    }

    public String enderecoOuNaoInformado() {
        return textoOuPadrao(endereco, "nao informado");
    }

    public String formasPagamentoOuNaoInformado() {
        return textoOuPadrao(formasPagamento, "nao informado");
    }

    public String condicoesParcelamentoOuNaoInformado() {
        return textoOuPadrao(condicoesParcelamento, "nao informado");
    }

    public String entregaOuNaoInformado() {
        return textoOuPadrao(entrega, "nao informado");
    }

    public String cidadesAtendidasOuNaoInformado() {
        if (!hasCidadesAtendidas()) {
            return "nao informado";
        }

        return cidadesAtendidas.stream()
                .filter(this::temTexto)
                .map(String::trim)
                .collect(Collectors.joining(", "));
    }

    public String ufOuNaoInformado() {
        return textoOuPadrao(uf, "nao informado");
    }

    public String telefoneOuNaoInformado() {
        return textoOuPadrao(telefone, "nao informado");
    }

    public String siteOuNaoInformado() {
        return textoOuPadrao(site, "nao informado");
    }

    private boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private String textoOuPadrao(String valor, String padrao) {
        return temTexto(valor) ? valor.trim() : padrao;
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase();
    }
}
