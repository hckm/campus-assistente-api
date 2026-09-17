package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.InterpretacaoIaResponseDTO;
import br.edu.usc.campusiachatbot.enums.TipoSolicitacaoEnum;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "gemini.api-key=")
@ActiveProfiles("test")
class GeminiServiceTest {

    @Autowired
    private GeminiService geminiService;

    @Test
    void deveClassificarReclamacaoLocalmente() {
        InterpretacaoIaResponseDTO resposta = interpretar("Quero reclamar do meu pedido");

        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.RECLAMACAO);
        assertThat(resposta.necessitaAtendimentoHumano()).isTrue();
        assertThat(resposta.respostaGerada()).contains("equipe responsavel").doesNotContainIgnoringCase("humano");
    }

    @Test
    void deveOrientarDuvidaFarmaceuticaComTerminologiaDaEquipe() {
        InterpretacaoIaResponseDTO resposta = interpretar("Esse produto tem contraindicacao?");

        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.DUVIDA_FARMACEUTICA);
        assertThat(resposta.necessitaAtendimentoHumano()).isTrue();
        assertThat(resposta.respostaGerada())
                .contains("equipe farmaceutica", "farmaceutico")
                .doesNotContainIgnoringCase("humano");
    }

    @Test
    void deveClassificarOrcamentoFormulaLocalmente() {
        InterpretacaoIaResponseDTO resposta = interpretar("Preciso de um orcamento para formula manipulada");

        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.ORCAMENTO_FORMULA);
        assertThat(resposta.necessitaAtendimentoHumano()).isTrue();
    }

    @Test
    void deveClassificarComprarProdutoLocalmente() {
        InterpretacaoIaResponseDTO resposta = interpretar("Quero comprar aquele soro facial");

        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.COMPRA_PRODUTO);
        assertThat(resposta.necessitaAtendimentoHumano()).isFalse();
    }

    @Test
    void deveClassificarEnvioReceitaLocalmente() {
        InterpretacaoIaResponseDTO resposta = interpretar("Tenho uma receita para enviar");

        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.ENVIO_RECEITA);
        assertThat(resposta.necessitaAtendimentoHumano()).isTrue();
    }

    @Test
    void deveClassificarStatusPedidoLocalmente() {
        InterpretacaoIaResponseDTO resposta = interpretar("Quero saber o status do meu pedido");

        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.STATUS_PEDIDO);
        assertThat(resposta.necessitaAtendimentoHumano()).isFalse();
    }

    @Test
    void deveClassificarRecompraLocalmente() {
        InterpretacaoIaResponseDTO resposta = interpretar("Gostaria de recomprar o mesmo produto de antes");

        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.RECOMPRA);
        assertThat(resposta.necessitaAtendimentoHumano()).isFalse();
    }

    @Test
    void deveClassificarOutrosLocalmente() {
        InterpretacaoIaResponseDTO resposta = interpretar("Bom dia tudo bem");

        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.OUTROS);
        assertThat(resposta.necessitaAtendimentoHumano()).isTrue();
    }

    @Test
    void deveNormalizarConfiancaDe0a1Para0a100() {
        InterpretacaoIaResponseDTO resposta = interpretar("Quero reclamar");

        assertThat(resposta.confianca()).isGreaterThan(1.0);
    }

    private InterpretacaoIaResponseDTO interpretar(String mensagem) {
        return geminiService.interpretarMensagem(
                new ChatbotRequestDTO("14999999999", "Teste", mensagem, null)
        );
    }
}
