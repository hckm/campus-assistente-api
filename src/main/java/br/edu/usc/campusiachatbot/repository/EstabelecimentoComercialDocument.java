package br.edu.usc.campusiachatbot.repository;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class EstabelecimentoComercialDocument {

    private String id;
    private String estabelecimentoId;
    private String tipo;
    private int schemaVersion;
    private String nome;
    private String segmento;
    private String horarioFuncionamento;
    private String endereco;
    private String formasPagamento;
    private String condicoesParcelamento;
    private String entrega;
    private List<String> cidadesAtendidas;
    private String uf;
    private String telefone;
    private String site;
}
