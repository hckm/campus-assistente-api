package br.edu.usc.campusiachatbot.repository;

import br.edu.usc.campusiachatbot.entity.MensagemAtendimentoEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MensagemAtendimentoRepository extends JpaRepository<MensagemAtendimentoEntity, Long> {

    @Query("SELECT m FROM MensagemAtendimentoEntity m WHERE m.atendimento.id = :atendimentoId "
            + "ORDER BY m.dataMensagem DESC, m.id DESC")
    List<MensagemAtendimentoEntity> findUltimasMensagens(@Param("atendimentoId") Long atendimentoId, Pageable pageable);
}
