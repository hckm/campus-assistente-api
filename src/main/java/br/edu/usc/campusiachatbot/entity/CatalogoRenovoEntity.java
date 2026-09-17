package br.edu.usc.campusiachatbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(
        name = "catalogo_renovo",
        uniqueConstraints = @UniqueConstraint(name = "uk_catalogo_renovo_codigo", columnNames = "codigo_catalogo"),
        indexes = {
                @Index(name = "idx_catalogo_renovo_categoria", columnList = "categoria"),
                @Index(name = "idx_catalogo_renovo_produto", columnList = "produto")
        }
)
@Getter
@Setter
public class CatalogoRenovoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer codigoCatalogo;

    @Column(nullable = false, length = 60)
    private String categoria;

    @Column(nullable = false, length = 160)
    private String produto;

    @Column(nullable = false, length = 1000)
    private String descricao;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoAtual;

    @Column(precision = 10, scale = 2)
    private BigDecimal precoOriginal;

    @Column(length = 255)
    private String urlCatalogo;

}
