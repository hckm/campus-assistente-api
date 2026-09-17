package br.edu.usc.campusiachatbot.dto;

public record CepLookupResponseDTO(
        String cep,
        String logradouro,
        String complemento,
        String bairro,
        String localidade,
        String uf,
        Boolean erro
) {
    public CepLookupResponseDTO(String cep, String localidade, String uf, Boolean erro) {
        this(cep, null, null, null, localidade, uf, erro);
    }

    public boolean valido() {
        return erro == null || !erro;
    }
}
