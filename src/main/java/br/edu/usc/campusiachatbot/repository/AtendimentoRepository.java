package br.edu.usc.campusiachatbot.repository;

import br.edu.usc.campusiachatbot.entity.AtendimentoEntity;
import br.edu.usc.campusiachatbot.enums.StatusAtendimentoEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AtendimentoRepository extends JpaRepository<AtendimentoEntity, Long> {

    List<AtendimentoEntity> findByStatusOrderByDataProcessamentoDesc(StatusAtendimentoEnum status);

    Page<AtendimentoEntity> findByStatus(StatusAtendimentoEnum status, Pageable pageable);

    Optional<AtendimentoEntity> findFirstByTelefoneClienteAndStatusInAndDataProcessamentoAfterOrderByDataProcessamentoDesc(
            String telefoneCliente,
            List<StatusAtendimentoEnum> statuses,
            LocalDateTime after);
}
