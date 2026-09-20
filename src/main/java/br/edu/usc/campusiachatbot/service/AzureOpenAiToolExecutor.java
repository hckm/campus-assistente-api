package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.client.AzureOpenAiChatResponse;
import br.edu.usc.campusiachatbot.client.AzureOpenAiClient;
import br.edu.usc.campusiachatbot.client.AzureOpenAiClientException;
import br.edu.usc.campusiachatbot.client.AzureOpenAiToolCall;
import br.edu.usc.campusiachatbot.config.CatalogoConsultaProperties;
import br.edu.usc.campusiachatbot.config.EstabelecimentoProperties;
import br.edu.usc.campusiachatbot.domain.ProdutoCatalogo;
import br.edu.usc.campusiachatbot.store.CatalogoStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AzureOpenAiToolExecutor {

    private static final int MAX_RODADAS = 4;
    private static final int MAX_CHAMADAS_DE_FERRAMENTA = 6;
    private static final BigDecimal MAX_PRECO = new BigDecimal("99999999.99");

    private static final String LISTAR_CATEGORIAS = "listar_categorias";
    private static final String BUSCAR_POR_CATEGORIA = "buscar_produtos_por_categoria";
    private static final String BUSCAR_POR_NOME = "buscar_produtos_por_nome";
    private static final String BUSCAR_POR_PRECO = "buscar_produtos_por_faixa_preco";
    private static final String CONSULTAR_ESTABELECIMENTO = "consultar_estabelecimento";
    private static final Set<String> FERRAMENTAS_CATALOGO = Set.of(
            LISTAR_CATEGORIAS,
            BUSCAR_POR_CATEGORIA,
            BUSCAR_POR_NOME,
            BUSCAR_POR_PRECO
    );

    private static final List<Map<String, Object>> FERRAMENTAS = List.of(
            ferramenta(
                    LISTAR_CATEGORIAS,
                    "Lista as categorias reais disponíveis no catálogo. Use quando o cliente perguntar genericamente pelos tipos de produto ou indicar uma finalidade sem informar o nome exato da categoria.",
                    Map.of(),
                    List.of()
            ),
            ferramenta(
                    BUSCAR_POR_CATEGORIA,
                    "Busca produtos reais por categoria. Use o nome exato obtido em listar_categorias quando o cliente descrever apenas uma finalidade.",
                    Map.of("categoria", campoTexto("Categoria informada pelo cliente.")),
                    List.of("categoria")
            ),
            ferramenta(
                    BUSCAR_POR_NOME,
                    "Busca produtos reais por nome ou parte do nome. Não use uma finalidade, como emagrecer, como nome de produto. Não use para sintomas, diagnóstico, dose ou indicação clínica.",
                    Map.of("termo", campoTexto("Nome ou parte do nome do produto.")),
                    List.of("termo")
            ),
            ferramenta(
                    BUSCAR_POR_PRECO,
                    "Busca produtos reais dentro de uma faixa de preço inclusiva.",
                    Map.of(
                            "precoMinimo", campoNumero("Preço mínimo em reais."),
                            "precoMaximo", campoNumero("Preço máximo em reais.")
                    ),
                    List.of("precoMinimo", "precoMaximo")
            ),
            ferramenta(
                    CONSULTAR_ESTABELECIMENTO,
                    "Consulta dados oficiais do estabelecimento. Use para combinar informações administrativas com a conversa comercial.",
                    Map.of("assunto", Map.of(
                            "type", "string",
                            "enum", List.of(
                                    "DADOS_GERAIS",
                                    "HORARIO",
                                    "ENDERECO",
                                    "PAGAMENTO",
                                    "ENTREGA",
                                    "CIDADES",
                                    "TELEFONE",
                                    "SITE"
                            ),
                            "description", "Informação administrativa necessária."
                    )),
                    List.of("assunto")
            )
    );

    private final CatalogoStore catalogoStore;
    private final EstabelecimentoComercialProvider estabelecimentoProvider;
    private final CatalogoConsultaProperties properties;
    private final ObjectMapper objectMapper;

    public String executar(
            AzureOpenAiClient client,
            List<Map<String, Object>> mensagensIniciais
    ) {
        List<Map<String, Object>> mensagens = new ArrayList<>(mensagensIniciais);
        int chamadasExecutadas = 0;
        boolean catalogoConsultado = false;

        for (int rodada = 0; rodada < MAX_RODADAS; rodada++) {
            AzureOpenAiChatResponse response = client.obterRespostaComFerramentas(mensagens, FERRAMENTAS);
            if (!response.hasToolCalls()) {
                if (!catalogoConsultado && respostaExigeConsultaCatalogo(response.content())) {
                    throw respostaInvalida("Azure OpenAI respondeu sobre produtos sem consultar o catalogo");
                }
                return response.content();
            }

            chamadasExecutadas += response.toolCalls().size();
            if (chamadasExecutadas > MAX_CHAMADAS_DE_FERRAMENTA) {
                throw respostaInvalida("Azure OpenAI excedeu o limite de chamadas de ferramenta");
            }

            mensagens.add(mensagemAssistente(response.toolCalls()));
            for (AzureOpenAiToolCall toolCall : response.toolCalls()) {
                log.debug("Executando ferramenta somente leitura do Azure OpenAI: {}", toolCall.name());
                catalogoConsultado = catalogoConsultado || FERRAMENTAS_CATALOGO.contains(toolCall.name());
                String resultado = executarFerramenta(toolCall);
                mensagens.add(Map.of(
                        "role", "tool",
                        "tool_call_id", toolCall.id(),
                        "content", resultado
                ));
            }
        }

        throw respostaInvalida("Azure OpenAI nao concluiu a resposta dentro do limite de rodadas");
    }

    private String executarFerramenta(AzureOpenAiToolCall toolCall) {
        JsonNode argumentos = argumentos(toolCall.arguments());
        Object dados = switch (toolCall.name()) {
            case LISTAR_CATEGORIAS -> listarCategorias(argumentos);
            case BUSCAR_POR_CATEGORIA -> buscarPorCategoria(argumentos);
            case BUSCAR_POR_NOME -> buscarPorNome(argumentos);
            case BUSCAR_POR_PRECO -> buscarPorPreco(argumentos);
            case CONSULTAR_ESTABELECIMENTO -> consultarEstabelecimento(argumentos);
            default -> throw respostaInvalida("Azure OpenAI solicitou uma ferramenta desconhecida");
        };
        return serializarResultado(dados);
    }

    private List<String> listarCategorias(JsonNode argumentos) {
        validarCampos(argumentos, Set.of());
        return catalogoStore.listarCategoriasLimitadas(properties.limiteItensResolvido()).stream()
                .map(categoria -> sanitizar(categoria, 60))
                .toList();
    }

    private List<Map<String, Object>> buscarPorCategoria(JsonNode argumentos) {
        validarCampos(argumentos, Set.of("categoria"));
        String categoria = texto(argumentos, "categoria", 60);
        return produtos(buscarCategoriaComResolucao(categoria));
    }

    private List<Map<String, Object>> buscarPorNome(JsonNode argumentos) {
        validarCampos(argumentos, Set.of("termo"));
        String termo = texto(argumentos, "termo", 160);
        List<ProdutoCatalogo> encontrados = catalogoStore.pesquisarPorProdutoLimitado(
                termo,
                properties.limiteItensResolvido()
        );
        return produtos(encontrados.isEmpty() ? buscarCategoriaResolvida(termo) : encontrados);
    }

    private List<ProdutoCatalogo> buscarCategoriaComResolucao(String termo) {
        List<ProdutoCatalogo> encontrados = catalogoStore.listarPorCategoriaLimitada(
                termo,
                properties.limiteItensResolvido()
        );
        return encontrados.isEmpty() ? buscarCategoriaResolvida(termo) : encontrados;
    }

    private List<ProdutoCatalogo> buscarCategoriaResolvida(String termo) {
        String categoria = CatalogoCategoriaMatcher.resolver(
                        termo,
                        catalogoStore.listarCategoriasLimitadas(CatalogoStore.LIMITE_MAXIMO_CONSULTA_IA)
                )
                .orElse(null);
        if (categoria == null) {
            return List.of();
        }
        return catalogoStore.listarPorCategoriaLimitada(
                categoria,
                properties.limiteItensResolvido()
        );
    }

    private boolean respostaExigeConsultaCatalogo(String conteudo) {
        try {
            JsonNode resposta = objectMapper.readTree(conteudo);
            return resposta != null
                    && "COMPRA_PRODUTO".equals(resposta.path("tipoSolicitacao").asText());
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private List<Map<String, Object>> buscarPorPreco(JsonNode argumentos) {
        validarCampos(argumentos, Set.of("precoMinimo", "precoMaximo"));
        BigDecimal minimo = decimal(argumentos, "precoMinimo");
        BigDecimal maximo = decimal(argumentos, "precoMaximo");
        if (minimo.signum() < 0
                || maximo.signum() < 0
                || minimo.compareTo(maximo) > 0
                || maximo.compareTo(MAX_PRECO) > 0
                || !centavosValidos(minimo)
                || !centavosValidos(maximo)) {
            throw respostaInvalida("Azure OpenAI informou uma faixa de preco invalida");
        }
        return produtos(catalogoStore.listarPorFaixaDePrecoLimitada(
                minimo,
                maximo,
                properties.limiteItensResolvido()
        ));
    }

    private Map<String, Object> consultarEstabelecimento(JsonNode argumentos) {
        validarCampos(argumentos, Set.of("assunto"));
        String assunto = texto(argumentos, "assunto", 30);
        EstabelecimentoProperties estabelecimento = estabelecimentoProvider.obter();
        return switch (assunto) {
            case "HORARIO" -> Map.of("horarioFuncionamento", estabelecimento.horarioFuncionamentoOuNaoInformado());
            case "ENDERECO" -> Map.of(
                    "endereco", estabelecimento.enderecoOuNaoInformado(),
                    "uf", estabelecimento.ufOuNaoInformado()
            );
            case "PAGAMENTO" -> Map.of(
                    "formasPagamento", estabelecimento.formasPagamentoOuNaoInformado(),
                    "condicoesParcelamento", estabelecimento.condicoesParcelamentoOuNaoInformado()
            );
            case "ENTREGA" -> Map.of(
                    "entrega", estabelecimento.entregaOuNaoInformado(),
                    "cidadesMotoboy", estabelecimento.cidadesAtendidasOuNaoInformado(),
                    "modalidades", "Correio e Transportadora para todo o Brasil; Motoboy nas cidades atendidas; Retirada na loja"
            );
            case "CIDADES" -> Map.of(
                    "cidadesAtendidasMotoboy", estabelecimento.cidadesAtendidasOuNaoInformado(),
                    "uf", estabelecimento.ufOuNaoInformado()
            );
            case "TELEFONE" -> Map.of("telefone", estabelecimento.telefoneOuNaoInformado());
            case "SITE" -> Map.of("site", estabelecimento.siteOuNaoInformado());
            case "DADOS_GERAIS" -> dadosGerais(estabelecimento);
            default -> throw respostaInvalida("Azure OpenAI informou um assunto administrativo invalido");
        };
    }

    private Map<String, Object> dadosGerais(EstabelecimentoProperties estabelecimento) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("nome", estabelecimento.nomeOuPadrao());
        dados.put("tipo", estabelecimento.tipoOuPadrao());
        dados.put("horarioFuncionamento", estabelecimento.horarioFuncionamentoOuNaoInformado());
        dados.put("endereco", estabelecimento.enderecoOuNaoInformado());
        dados.put("formasPagamento", estabelecimento.formasPagamentoOuNaoInformado());
        dados.put("condicoesParcelamento", estabelecimento.condicoesParcelamentoOuNaoInformado());
        dados.put("entrega", estabelecimento.entregaOuNaoInformado());
        dados.put("cidadesAtendidasMotoboy", estabelecimento.cidadesAtendidasOuNaoInformado());
        dados.put("uf", estabelecimento.ufOuNaoInformado());
        dados.put("telefone", estabelecimento.telefoneOuNaoInformado());
        dados.put("site", estabelecimento.siteOuNaoInformado());
        return Map.copyOf(dados);
    }

    private List<Map<String, Object>> produtos(List<ProdutoCatalogo> encontrados) {
        return encontrados.stream()
                .limit(properties.limiteItensResolvido())
                .map(this::produto)
                .toList();
    }

    private Map<String, Object> produto(ProdutoCatalogo produto) {
        Map<String, Object> dados = new LinkedHashMap<>();
        if (produto.codigoCatalogo() != null) {
            dados.put("codigo", produto.codigoCatalogo());
        }
        dados.put("categoria", sanitizar(produto.categoria(), 60));
        dados.put("nome", sanitizar(produto.produto(), 160));
        dados.put("descricao", sanitizar(produto.descricao(), 300));
        if (produto.precoAtual() != null) {
            dados.put("precoAtual", produto.precoAtual());
        }
        if (produto.precoOriginal() != null) {
            dados.put("precoOriginal", produto.precoOriginal());
        }
        if (produto.urlCatalogo() != null && !produto.urlCatalogo().isBlank()) {
            dados.put("urlCatalogo", sanitizar(produto.urlCatalogo(), 255));
        }
        return Map.copyOf(dados);
    }

    private JsonNode argumentos(String json) {
        try {
            JsonNode argumentos = objectMapper.readTree(json);
            if (argumentos == null || !argumentos.isObject()) {
                throw respostaInvalida("Azure OpenAI informou argumentos de ferramenta invalidos");
            }
            return argumentos;
        } catch (JsonProcessingException exception) {
            throw new AzureOpenAiClientException(
                    AzureOpenAiClientException.Reason.INVALID_OUTPUT,
                    "Azure OpenAI informou argumentos de ferramenta invalidos",
                    exception
            );
        }
    }

    private void validarCampos(JsonNode argumentos, Set<String> esperados) {
        Set<String> recebidos = new java.util.HashSet<>();
        argumentos.fieldNames().forEachRemaining(recebidos::add);
        if (!recebidos.equals(esperados)) {
            throw respostaInvalida("Azure OpenAI informou campos inesperados na ferramenta");
        }
    }

    private String texto(JsonNode argumentos, String campo, int limite) {
        JsonNode valor = argumentos.get(campo);
        if (valor == null || !valor.isTextual()) {
            throw respostaInvalida("Azure OpenAI informou um argumento textual invalido");
        }
        String texto = valor.textValue().trim();
        if (texto.isBlank() || texto.length() > limite) {
            throw respostaInvalida("Azure OpenAI informou um argumento textual fora do limite");
        }
        return texto;
    }

    private BigDecimal decimal(JsonNode argumentos, String campo) {
        JsonNode valor = argumentos.get(campo);
        if (valor == null || !valor.isNumber()) {
            throw respostaInvalida("Azure OpenAI informou um argumento numerico invalido");
        }
        return valor.decimalValue();
    }

    private boolean centavosValidos(BigDecimal valor) {
        try {
            valor.movePointRight(2).longValueExact();
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private String serializarResultado(Object dados) {
        try {
            String resultado = objectMapper.writeValueAsString(Map.of(
                    "fonte", "backend_renovo",
                    "somenteDados", true,
                    "dados", dados
            ));
            if (resultado.length() > properties.maxCaracteresContextoResolvido()
                    || resultado.getBytes(StandardCharsets.UTF_8).length > properties.maxBytesContextoResolvido()) {
                throw respostaInvalida("Resultado da ferramenta excedeu o limite seguro de contexto");
            }
            return resultado;
        } catch (JsonProcessingException exception) {
            throw new AzureOpenAiClientException(
                    AzureOpenAiClientException.Reason.INVALID_OUTPUT,
                    "Nao foi possivel serializar o resultado da ferramenta",
                    exception
            );
        }
    }

    private Map<String, Object> mensagemAssistente(List<AzureOpenAiToolCall> toolCalls) {
        List<Map<String, Object>> chamadas = toolCalls.stream()
                .map(toolCall -> Map.<String, Object>of(
                        "id", toolCall.id(),
                        "type", "function",
                        "function", Map.of(
                                "name", toolCall.name(),
                                "arguments", toolCall.arguments()
                        )
                ))
                .toList();
        return Map.of("role", "assistant", "tool_calls", chamadas);
    }

    private String sanitizar(String valor, int limite) {
        String seguro = valor == null ? "nao informado" : valor
                .replaceAll("[\\p{Cntrl}]", " ")
                .replace("RESULTADO_CATALOGO_NAO_CONFIAVEL_INICIO", "DADO_CATALOGO")
                .replace("RESULTADO_CATALOGO_NAO_CONFIAVEL_FIM", "DADO_CATALOGO")
                .trim();
        if (seguro.isBlank()) {
            return "nao informado";
        }
        return seguro.length() <= limite ? seguro : seguro.substring(0, limite);
    }

    private AzureOpenAiClientException respostaInvalida(String mensagem) {
        return new AzureOpenAiClientException(
                AzureOpenAiClientException.Reason.INVALID_OUTPUT,
                mensagem
        );
    }

    private static Map<String, Object> ferramenta(
            String nome,
            String descricao,
            Map<String, Object> propriedades,
            List<String> obrigatorios
    ) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", nome,
                        "description", descricao,
                        "strict", true,
                        "parameters", Map.of(
                                "type", "object",
                                "properties", propriedades,
                                "required", obrigatorios,
                                "additionalProperties", false
                        )
                )
        );
    }

    private static Map<String, Object> campoTexto(String descricao) {
        return Map.of("type", "string", "description", descricao);
    }

    private static Map<String, Object> campoNumero(String descricao) {
        return Map.of("type", "number", "description", descricao);
    }
}
