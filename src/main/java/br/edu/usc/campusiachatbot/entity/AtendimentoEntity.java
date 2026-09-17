package br.edu.usc.campusiachatbot.entity;

import br.edu.usc.campusiachatbot.enums.CategoriaAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.OrigemMensagemEnum;
import br.edu.usc.campusiachatbot.enums.StatusAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.TipoSolicitacaoEnum;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "atendimentos")
@Getter
@Setter
public class AtendimentoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String telefoneCliente;

    @Column(length = 120)
    private String nomeCliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrigemMensagemEnum origem;

    @Column(nullable = false, length = 4000)
    private String mensagemCliente;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private TipoSolicitacaoEnum tipoSolicitacao;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private CategoriaAtendimentoEnum categoria;

    @Column(length = 4000)
    private String respostaGerada;

    @Column(nullable = false)
    private boolean necessitaAtendimentoHumano;

    @Column(length = 1000)
    private String motivoEncaminhamento;

    private Double confianca;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StatusAtendimentoEnum status;

    @Column(nullable = false)
    private LocalDateTime dataProcessamento;

    @OneToMany(mappedBy = "atendimento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MensagemAtendimentoEntity> mensagens = new ArrayList<>();

}
