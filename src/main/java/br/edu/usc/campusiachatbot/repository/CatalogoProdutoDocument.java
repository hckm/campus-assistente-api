package br.edu.usc.campusiachatbot.repository;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CatalogoProdutoDocument {

    private String id;
    private String catalogoId;
    private String tipo;
    private int schemaVersion;
    private String legacyId;
    private Integer codigoCatalogo;
    private String categoria;
    private String categoriaNormalizada;
    private String produto;
    private String produtoNormalizado;
    private String descricao;
    private Long precoAtualCentavos;
    private Long precoOriginalCentavos;
    private String urlCatalogo;
}
