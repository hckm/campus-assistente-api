package br.edu.usc.campusiachatbot.entity;

import br.edu.usc.campusiachatbot.enums.DirecaoMensagemEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "mensagens_atendimento")
@Getter
@Setter
public class MensagemAtendimentoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atendimento_id", nullable = false)
    private AtendimentoEntity atendimento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DirecaoMensagemEnum direcao;

    @Column(nullable = false, length = 4000)
    private String conteudo;

    @Column(nullable = false)
    private LocalDateTime dataMensagem;

}
