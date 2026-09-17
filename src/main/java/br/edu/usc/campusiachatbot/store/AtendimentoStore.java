package br.edu.usc.campusiachatbot.store;

import br.edu.usc.campusiachatbot.domain.Atendimento;
import br.edu.usc.campusiachatbot.domain.InicioInteracao;
import br.edu.usc.campusiachatbot.domain.MensagemConversa;
import br.edu.usc.campusiachatbot.domain.Pagina;
import br.edu.usc.campusiachatbot.enums.StatusAtendimentoEnum;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AtendimentoStore {

    InicioInteracao iniciarInteracao(
            Atendimento atendimento,
            List<StatusAtendimentoEnum> statuses,
            LocalDateTime limite,
            LocalDateTime processamentoExpiraEm,
            String idempotencyKey,
            String payloadHash
    );

    Optional<Atendimento> buscarConversaAtiva(
            String telefoneCliente,
            List<StatusAtendimentoEnum> statuses,
            LocalDateTime limite
    );

    List<MensagemConversa> buscarHistoricoMensagens(Atendimento atendimento, int limite);

    Atendimento registrarRespostaComMensagemBot(InicioInteracao interacao, Atendimento atendimento);

    void marcarErro(InicioInteracao interacao, LocalDateTime dataProcessamento);

    Pagina<Atendimento> listarTodosOrdenadosPorDataProcessamentoDesc(String token, int tamanho);

    Optional<Atendimento> buscarPorId(String id);

    Pagina<Atendimento> listarPorStatusOrdenadosPorDataProcessamentoDesc(
            StatusAtendimentoEnum status,
            String token,
            int tamanho
    );
}
