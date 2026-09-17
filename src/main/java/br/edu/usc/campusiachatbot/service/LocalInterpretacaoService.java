package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.dto.InterpretacaoIaResponseDTO;
import br.edu.usc.campusiachatbot.enums.CategoriaAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.TipoSolicitacaoEnum;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;

@Service
public class LocalInterpretacaoService {

    public InterpretacaoIaResponseDTO interpretar(String mensagem) {
        String texto = normalizar(mensagem);

        if (contem(texto, "reclam", "problema", "atraso", "errado")) {
            return new InterpretacaoIaResponseDTO(
                    TipoSolicitacaoEnum.RECLAMACAO,
                    CategoriaAtendimentoEnum.RECLAMACAO,
                    "Sinto muito pelo ocorrido. Vou registrar sua reclamacao e encaminhar para a equipe responsavel.",
                    true,
                    "Reclamacao exige avaliacao da equipe responsavel.",
                    0.82
            ).normalizado();
        }

        if (contem(texto, "dose", "dosagem", "tomar", "posologia", "efeito colateral", "reacao", "interacao", "gravidez", "gravida", "amament", "crianca", "bebe", "sintoma", "substituir", "contraindic", "medicamento", "remedio")) {
            return respostaFarmaceutica().normalizado();
        }

        if (contem(texto, "orcamento", "formula", "manipulad")) {
            return new InterpretacaoIaResponseDTO(
                    TipoSolicitacaoEnum.ORCAMENTO_FORMULA,
                    CategoriaAtendimentoEnum.ATENDIMENTO_COMERCIAL,
                    "Claro. Para solicitar o orcamento, envie a receita e informe se deseja retirada ou entrega.",
                    true,
                    "Necessario analise da receita ou formula pela farmacia.",
                    0.88
            ).normalizado();
        }

        if (contem(texto, "quero comprar", "gostaria de comprar", "pedido do produto", "quero pedir", "gostaria de pedir", "solicitar produto", "fazer um pedido")) {
            return new InterpretacaoIaResponseDTO(
                    TipoSolicitacaoEnum.COMPRA_PRODUTO,
                    CategoriaAtendimentoEnum.ATENDIMENTO_COMERCIAL,
                    "Claro, vou ajudar com seu pedido! Para prosseguir, preciso das seguintes informacoes:\n" +
                            "1. Quantas unidades deseja?\n" +
                            "2. Qual a forma de pagamento preferida?\n" +
                            "3. Como prefere receber: Correio, Transportadora, Motoboy (se na cidade) ou Retirada na loja?\n" +
                            "4. Qual o endereco completo para entrega (rua, numero, bairro, cidade, CEP)? Se for retirada, confirme.",
                    false,
                    null,
                    0.85
            ).normalizado();
        }

        if (contem(texto, "receita")) {
            return new InterpretacaoIaResponseDTO(
                    TipoSolicitacaoEnum.ENVIO_RECEITA,
                    CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                    "Voce pode enviar a receita por este atendimento. A equipe da farmacia fara a analise antes de prosseguir.",
                    true,
                    "Receita precisa ser analisada pela farmacia.",
                    0.84
            ).normalizado();
        }

        if (contem(texto, "pedido", "status", "andamento", "pronto")) {
            return new InterpretacaoIaResponseDTO(
                    TipoSolicitacaoEnum.STATUS_PEDIDO,
                    CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                    "Para consultar o status do pedido, informe o numero do pedido ou o CPF cadastrado.",
                    false,
                    null,
                    0.86
            ).normalizado();
        }

        if (contem(texto, "entrega", "delivery", "cep", "frete")) {
            return new InterpretacaoIaResponseDTO(
                    TipoSolicitacaoEnum.ENTREGA,
                    CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                    "Fazemos verificacao de entrega por regiao. Informe seu CEP para consultarmos a disponibilidade.",
                    false,
                    null,
                    0.9
            ).normalizado();
        }

        if (contem(texto, "pagamento", "cartao", "pix", "dinheiro", "boleto")) {
            return new InterpretacaoIaResponseDTO(
                    TipoSolicitacaoEnum.FORMAS_PAGAMENTO,
                    CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                    "Aceitamos formas de pagamento conforme disponibilidade da farmacia. Informe seu pedido para confirmarmos as opcoes.",
                    false,
                    null,
                    0.88
            ).normalizado();
        }

        if (contem(texto, "horario", "funcionamento", "abre", "fecha")) {
            return new InterpretacaoIaResponseDTO(
                    TipoSolicitacaoEnum.HORARIO_FUNCIONAMENTO,
                    CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                    "Posso ajudar com o horario de funcionamento. Confirme a unidade desejada para informarmos corretamente.",
                    false,
                    null,
                    0.86
            ).normalizado();
        }

        if (contem(texto, "recompr", "comprar novamente", "mesmo pedido")) {
            return new InterpretacaoIaResponseDTO(
                    TipoSolicitacaoEnum.RECOMPRA,
                    CategoriaAtendimentoEnum.ATENDIMENTO_COMERCIAL,
                    "Para recompra, informe o numero do pedido anterior ou os dados do cadastro para verificarmos.",
                    false,
                    null,
                    0.82
            ).normalizado();
        }

        return new InterpretacaoIaResponseDTO(
                TipoSolicitacaoEnum.OUTROS,
                CategoriaAtendimentoEnum.OUTROS,
                "Recebemos sua mensagem. Vou direcionar seu atendimento para a equipe responsavel.",
                true,
                "Solicitacao nao classificada automaticamente.",
                0.55
        ).normalizado();
    }

    private InterpretacaoIaResponseDTO respostaFarmaceutica() {
        return new InterpretacaoIaResponseDTO(
                TipoSolicitacaoEnum.DUVIDA_FARMACEUTICA,
                CategoriaAtendimentoEnum.ATENDIMENTO_FARMACEUTICO,
                "Essa orientacao depende de uma avaliacao individual. A equipe farmaceutica pode analisar seu caso com seguranca. Posso encaminhar sua duvida para um farmaceutico.",
                true,
                "Mensagem envolve orientacao farmaceutica ou clinica.",
                0.92
        );
    }

    private boolean contem(String texto, String... termos) {
        for (String termo : termos) {
            if (texto.contains(termo)) {
                return true;
            }
        }
        return false;
    }

    private String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT);
    }
}
