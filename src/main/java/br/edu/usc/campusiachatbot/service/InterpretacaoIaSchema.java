package br.edu.usc.campusiachatbot.service;

import java.util.List;
import java.util.Map;

public final class InterpretacaoIaSchema {

    private InterpretacaoIaSchema() {
    }

    public static Map<String, Object> criar() {
        Map<String, Object> consulta = Map.of(
                "type", List.of("object", "null"),
                "properties", Map.of(
                        "operacao", Map.of(
                                "type", "string",
                                "enum", List.of(
                                        "BUSCAR_CATEGORIA",
                                        "BUSCAR_PRODUTO",
                                        "BUSCAR_FAIXA_PRECO"
                                )
                        ),
                        "termo", Map.of("type", List.of("string", "null")),
                        "categoria", Map.of("type", List.of("string", "null")),
                        "precoMinimo", Map.of("type", List.of("number", "null")),
                        "precoMaximo", Map.of("type", List.of("number", "null"))
                ),
                "required", List.of(
                        "operacao",
                        "termo",
                        "categoria",
                        "precoMinimo",
                        "precoMaximo"
                ),
                "additionalProperties", false
        );
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "tipoSolicitacao", Map.of(
                                "type", "string",
                                "enum", List.of(
                                        "ORCAMENTO_FORMULA",
                                        "COMPRA_PRODUTO",
                                        "STATUS_PEDIDO",
                                        "ENVIO_RECEITA",
                                        "RECOMPRA",
                                        "HORARIO_FUNCIONAMENTO",
                                        "ENTREGA",
                                        "FORMAS_PAGAMENTO",
                                        "DUVIDA_ADMINISTRATIVA",
                                        "DUVIDA_FARMACEUTICA",
                                        "RECLAMACAO",
                                        "OUTROS"
                                )
                        ),
                        "categoria", Map.of(
                                "type", "string",
                                "enum", List.of(
                                        "ATENDIMENTO_COMERCIAL",
                                        "ATENDIMENTO_ADMINISTRATIVO",
                                        "ATENDIMENTO_FARMACEUTICO",
                                        "RECLAMACAO",
                                        "OUTROS"
                                )
                        ),
                        "respostaGerada", Map.of("type", "string"),
                        "necessitaAtendimentoHumano", Map.of("type", "boolean"),
                        "motivoEncaminhamento", Map.of("type", List.of("string", "null")),
                        "confianca", Map.of("type", "number"),
                        "solicitarCategorias", Map.of("type", "boolean"),
                        "consultaCatalogo", consulta
                ),
                "required", List.of(
                        "tipoSolicitacao",
                        "categoria",
                        "respostaGerada",
                        "necessitaAtendimentoHumano",
                        "motivoEncaminhamento",
                        "confianca",
                        "solicitarCategorias",
                        "consultaCatalogo"
                ),
                "additionalProperties", false
        );
    }
}
