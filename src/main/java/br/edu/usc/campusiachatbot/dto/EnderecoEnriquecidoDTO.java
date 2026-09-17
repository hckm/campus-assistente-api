package br.edu.usc.campusiachatbot.dto;

public record EnderecoEnriquecidoDTO(
        String cep,
        String logradouro,
        String bairro,
        String cidade,
        String uf,
        String origem
) {
    public static EnderecoEnriquecidoDTO vazio() {
        return new EnderecoEnriquecidoDTO(null, null, null, null, null, null);
    }

    public static EnderecoEnriquecidoDTO deCep(CepLookupResponseDTO cep) {
        return deCepLookup(cep, "CEP");
    }

    public static EnderecoEnriquecidoDTO deEndereco(CepLookupResponseDTO cep) {
        return deCepLookup(cep, "ENDERECO");
    }

    public boolean encontrado() {
        return temTexto(cidade) || temTexto(cep) || temTexto(logradouro);
    }

    public boolean temCidade() {
        return temTexto(cidade);
    }

    public String comoContextoPrompt() {
        if (!encontrado()) {
            return "nao identificado";
        }

        return """
                cep: %s
                logradouro: %s
                bairro: %s
                cidade: %s
                uf: %s
                origemValidacao: %s
                """.formatted(
                valorOuNaoInformado(cep),
                valorOuNaoInformado(logradouro),
                valorOuNaoInformado(bairro),
                valorOuNaoInformado(cidade),
                valorOuNaoInformado(uf),
                valorOuNaoInformado(origem)
        ).trim();
    }

    private static EnderecoEnriquecidoDTO deCepLookup(CepLookupResponseDTO cep, String origem) {
        if (cep == null) {
            return vazio();
        }

        return new EnderecoEnriquecidoDTO(
                cep.cep(),
                cep.logradouro(),
                cep.bairro(),
                cep.localidade(),
                cep.uf(),
                origem
        );
    }

    private static String valorOuNaoInformado(String valor) {
        return temTexto(valor) ? valor.trim() : "nao informado";
    }

    private static boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
