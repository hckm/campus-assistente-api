package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.config.CatalogoConsultaProperties;
import br.edu.usc.campusiachatbot.domain.MensagemConversa;
import br.edu.usc.campusiachatbot.domain.ProdutoCatalogo;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.EnderecoEnriquecidoDTO;
import br.edu.usc.campusiachatbot.dto.InterpretacaoIaResponseDTO;
import br.edu.usc.campusiachatbot.enums.CategoriaAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.TipoSolicitacaoEnum;
import br.edu.usc.campusiachatbot.store.CatalogoStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogoInterpretacaoOrchestrator {

    private static final Set<String> CAMPOS_RAIZ = Set.of(
            "tipoSolicitacao",
            "categoria",
            "respostaGerada",
            "necessitaAtendimentoHumano",
            "motivoEncaminhamento",
            "confianca",
            "solicitarCategorias",
            "consultaCatalogo"
    );
    private static final Set<String> CAMPOS_CONSULTA = Set.of(
            "operacao",
            "termo",
            "categoria",
            "precoMinimo",
            "precoMaximo"
    );
    private static final int MAX_TERMO = 160;
    private static final BigDecimal MAX_PRECO = new BigDecimal("99999999.99");

    private final PromptBuilderService promptBuilderService;
    private final LocalInterpretacaoService localInterpretacaoService;
    private final CatalogoStore catalogoStore;
    private final CatalogoConsultaProperties properties;
    private final ObjectMapper objectMapper;

    public InterpretacaoIaResponseDTO interpretar(
            ChatbotRequestDTO request,
            EnderecoEnriquecidoDTO enderecoEnriquecido,
            List<MensagemConversa> historico,
            InferenciaIa inferencia,
            String provedor
    ) {
        List<Map<String, Object>> contents = promptBuilderService.construirContents(
                request,
                enderecoEnriquecido,
                historico
        );
        RespostaInterpretacaoIaInterna planejamento;
        boolean primeiraInferenciaDisponivel = true;
        try {
            planejamento = validarResposta(inferencia.executar(contents));
        } catch (RuntimeException exception) {
            log.warn("Falha na primeira inferencia; provedor={}; tipoFalha={}",
                    provedor, exception.getClass().getSimpleName());
            primeiraInferenciaDisponivel = false;
            planejamento = planejamentoLocal(request.mensagem());
        }
        boolean categoriaPorFinalidade = mensagemDefineCategoriaPorFinalidade(request.mensagem());

        if ((planejamento.solicitarCategorias() || planejamento.consultaCatalogo() != null)
                && !fluxoCatalogoPermitido(planejamento)) {
            return planejamento.respostaPublica();
        }

        if (planejamento.solicitarCategorias() && !categoriaPorFinalidade) {
            if (planejamento.consultaCatalogo() != null) {
                return respostaRefinamentoInvalido(planejamento);
            }
            return listarCategorias(planejamento);
        }

        ConsultaCatalogoIa consultaMensagem = inferirConsultaCatalogo(request.mensagem());
        ConsultaCatalogoIa consulta = planejamento.consultaCatalogo();
        if (fluxoCatalogoPermitido(planejamento)
                && (consulta == null || categoriaPorFinalidade)) {
            consulta = consultaMensagem;
        }
        if (consulta == null && mensagemSolicitaCategorias(request.mensagem())) {
            return listarCategorias(planejamento);
        }
        if (consulta == null && fluxoCatalogoPermitido(planejamento)) {
            consulta = inferirConsultaCatalogo(request.mensagem());
        }
        if (consulta == null) {
            return planejamento.respostaPublica();
        }
        if (!consultaValida(consulta)) {
            return respostaRefinamentoInvalido(planejamento);
        }

        List<ProdutoCatalogo> produtos;
        try {
            produtos = executarConsulta(consulta);
        } catch (RuntimeException exception) {
            log.warn("Falha na consulta limitada do catalogo; operacao={}; tipoFalha={}",
                    consulta.operacao(), exception.getClass().getSimpleName());
            return respostaCatalogoIndisponivel(planejamento);
        }
        if (produtos.isEmpty()) {
            return respostaSemResultado(planejamento);
        }

        if (!primeiraInferenciaDisponivel) {
            return respostaDeterministicaProdutos(planejamento, produtos);
        }

        try {
            ResultadoContextoCatalogo contextoCatalogo = promptBuilderService.adicionarResultadoCatalogo(
                    contents,
                    produtos,
                    properties.maxCaracteresContextoResolvido(),
                    properties.maxBytesContextoResolvido()
            );
            if (!contextoCatalogo.possuiProdutos()) {
                return respostaContextoCatalogoVazio(planejamento);
            }
            List<ProdutoCatalogo> produtosContexto = produtos.stream()
                    .limit(contextoCatalogo.produtosSerializados())
                    .toList();
            RespostaInterpretacaoIaInterna respostaFinal = validarResposta(
                    inferencia.executar(contextoCatalogo.contents())
            );
            if (respostaFinal.solicitarCategorias()
                    || respostaFinal.consultaCatalogo() != null
                    || !respostaUsaResultados(respostaFinal.respostaGerada(), produtosContexto)) {
                return respostaDeterministicaProdutos(planejamento, produtosContexto);
            }
            return normalizarRespostaCatalogo(respostaFinal);
        } catch (RuntimeException exception) {
            log.warn("Falha na segunda inferencia; provedor={}; tipoFalha={}",
                    provedor, exception.getClass().getSimpleName());
            return respostaDeterministicaProdutos(planejamento, produtos);
        }
    }

    private RespostaInterpretacaoIaInterna planejamentoLocal(String mensagem) {
        InterpretacaoIaResponseDTO resposta = localInterpretacaoService.interpretar(mensagem);
        return new RespostaInterpretacaoIaInterna(
                resposta.tipoSolicitacao(),
                resposta.categoria(),
                resposta.respostaGerada(),
                Boolean.TRUE.equals(resposta.necessitaAtendimentoHumano()),
                resposta.motivoEncaminhamento(),
                resposta.confianca(),
                false,
                null
        );
    }

    private InterpretacaoIaResponseDTO listarCategorias(RespostaInterpretacaoIaInterna planejamento) {
        try {
            List<String> categorias = catalogoStore.listarCategoriasLimitadas(properties.limiteItensResolvido());
            if (categorias.isEmpty()) {
                return respostaCatalogoIndisponivel(planejamento);
            }
            return substituirRespostaCatalogo(
                    planejamento,
                    "As categorias disponiveis sao: " + String.join(", ", categorias)
                            + ". Qual categoria voce deseja consultar?",
                    false,
                    null
            );
        } catch (RuntimeException exception) {
            log.warn("Falha ao listar categorias limitadas; tipoFalha={}", exception.getClass().getSimpleName());
            return respostaCatalogoIndisponivel(planejamento);
        }
    }

    private List<ProdutoCatalogo> executarConsulta(ConsultaCatalogoIa consulta) {
        int limite = properties.limiteItensResolvido();
        List<ProdutoCatalogo> produtos = switch (consulta.operacao()) {
            case BUSCAR_CATEGORIA -> catalogoStore.listarPorCategoriaLimitada(consulta.categoria().trim(), limite);
            case BUSCAR_PRODUTO -> catalogoStore.pesquisarPorProdutoLimitado(consulta.termo().trim(), limite);
            case BUSCAR_FAIXA_PRECO -> catalogoStore.listarPorFaixaDePrecoLimitada(
                    consulta.precoMinimo(),
                    consulta.precoMaximo(),
                    limite
            );
        };
        return produtos.stream().limit(limite).toList();
    }

    private boolean fluxoCatalogoPermitido(RespostaInterpretacaoIaInterna planejamento) {
        return planejamento.tipoSolicitacao() == TipoSolicitacaoEnum.COMPRA_PRODUTO
                || planejamento.tipoSolicitacao() == TipoSolicitacaoEnum.OUTROS;
    }

    private boolean mensagemSolicitaCategorias(String mensagem) {
        String texto = normalizar(mensagem);
        return contemTodosOuPadroes(texto,
                "tipos de produto",
                "categorias de produto",
                "quais produtos voces tem",
                "que produtos voces tem",
                "o que voces tem no catalogo",
                "mostrar catalogo"
        );
    }

    private ConsultaCatalogoIa inferirConsultaCatalogo(String mensagem) {
        String texto = normalizar(mensagem);
        if (contemTodosOuPadroes(texto,
                "horario", "entrega", "frete", "cep", "pagamento", "cartao", "pix", "parcela",
                "dose", "dosagem", "sintoma", "receita", "formula", "reclam")) {
            return null;
        }

        Matcher faixa = Pattern.compile("\\bentre\\s+(\\d+(?:[.,]\\d{1,2})?)\\s+e\\s+(\\d+(?:[.,]\\d{1,2})?)")
                .matcher(texto);
        if (faixa.find()) {
            return new ConsultaCatalogoIa(
                    ConsultaCatalogoOperacao.BUSCAR_FAIXA_PRECO,
                    null,
                    null,
                    decimal(faixa.group(1)),
                    decimal(faixa.group(2))
            );
        }

        Matcher categoria = Pattern.compile(
                "\\bprodutos?\\s+(?:da\\s+categoria\\s+|de\\s+|para\\s+)"
                        + "([a-z0-9 _-]{2,60}?)(?=\\s+(?:voces|que)\\b|[?!.]*$)"
        ).matcher(texto);
        if (categoria.find()) {
            return new ConsultaCatalogoIa(
                    ConsultaCatalogoOperacao.BUSCAR_CATEGORIA,
                    null,
                    categoria.group(1).trim(),
                    null,
                    null
            );
        }

        Matcher produto = Pattern.compile("\\b(?:tem|possui|vende)\\s+(?:o|a|os|as)?\\s*([^?!.]{2,160})")
                .matcher(texto);
        if (produto.find()) {
            return new ConsultaCatalogoIa(
                    ConsultaCatalogoOperacao.BUSCAR_PRODUTO,
                    produto.group(1).trim(),
                    null,
                    null,
                    null
            );
        }
        return null;
    }

    private boolean mensagemDefineCategoriaPorFinalidade(String mensagem) {
        return Pattern.compile("\\bprodutos?\\s+para\\s+[a-z0-9 _-]{2,60}(?:[?!.]|$)")
                .matcher(normalizar(mensagem))
                .find();
    }

    private BigDecimal decimal(String valor) {
        return new BigDecimal(valor.replace(',', '.'));
    }

    private boolean contemTodosOuPadroes(String texto, String... termos) {
        for (String termo : termos) {
            if (texto.contains(termo)) {
                return true;
            }
        }
        return false;
    }

    private boolean consultaValida(ConsultaCatalogoIa consulta) {
        if (consulta.operacao() == null) {
            return false;
        }
        return switch (consulta.operacao()) {
            case BUSCAR_CATEGORIA -> textoValido(consulta.categoria())
                    && consulta.termo() == null
                    && consulta.precoMinimo() == null
                    && consulta.precoMaximo() == null;
            case BUSCAR_PRODUTO -> textoValido(consulta.termo())
                    && consulta.categoria() == null
                    && consulta.precoMinimo() == null
                    && consulta.precoMaximo() == null;
            case BUSCAR_FAIXA_PRECO -> consulta.termo() == null
                    && consulta.categoria() == null
                    && faixaValida(consulta.precoMinimo(), consulta.precoMaximo());
        };
    }

    private boolean textoValido(String valor) {
        return valor != null && !valor.isBlank() && valor.trim().length() <= MAX_TERMO;
    }

    private boolean faixaValida(BigDecimal minimo, BigDecimal maximo) {
        return minimo != null
                && maximo != null
                && minimo.signum() >= 0
                && maximo.signum() >= 0
                && minimo.compareTo(maximo) <= 0
                && maximo.compareTo(MAX_PRECO) <= 0
                && precoRepresentavel(minimo)
                && precoRepresentavel(maximo);
    }

    private boolean precoRepresentavel(BigDecimal valor) {
        try {
            valor.movePointRight(2).longValueExact();
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private RespostaInterpretacaoIaInterna validarResposta(String conteudo) {
        try {
            JsonNode raiz = objectMapper.readTree(removerMarkdown(conteudo));
            if (raiz == null || !raiz.isObject() || !campos(raiz).equals(CAMPOS_RAIZ)) {
                throw new IllegalArgumentException("Resposta da IA invalida");
            }
            validarTiposRaiz(raiz);
            JsonNode consulta = raiz.get("consultaCatalogo");
            if (!consulta.isNull()) {
                if (!consulta.isObject() || !campos(consulta).equals(CAMPOS_CONSULTA)) {
                    throw new IllegalArgumentException("Consulta de catalogo invalida");
                }
                validarTiposConsulta(consulta);
            }
            return objectMapper.treeToValue(raiz, RespostaInterpretacaoIaInterna.class);
        } catch (JsonProcessingException | NullPointerException exception) {
            throw new IllegalArgumentException("Resposta da IA invalida", exception);
        }
    }

    private void validarTiposRaiz(JsonNode raiz) {
        JsonNode resposta = raiz.get("respostaGerada");
        JsonNode confianca = raiz.get("confianca");
        if (!raiz.get("tipoSolicitacao").isTextual()
                || !raiz.get("categoria").isTextual()
                || !resposta.isTextual()
                || resposta.textValue().isBlank()
                || !raiz.get("necessitaAtendimentoHumano").isBoolean()
                || !(raiz.get("motivoEncaminhamento").isNull()
                || raiz.get("motivoEncaminhamento").isTextual())
                || !confianca.isNumber()
                || !Double.isFinite(confianca.doubleValue())
                || confianca.doubleValue() < 0
                || confianca.doubleValue() > 100
                || !raiz.get("solicitarCategorias").isBoolean()
                || !(raiz.get("consultaCatalogo").isNull() || raiz.get("consultaCatalogo").isObject())) {
            throw new IllegalArgumentException("Resposta da IA invalida");
        }
    }

    private void validarTiposConsulta(JsonNode consulta) {
        if (!consulta.get("operacao").isTextual()
                || !textoOuNulo(consulta.get("termo"))
                || !textoOuNulo(consulta.get("categoria"))
                || !numeroOuNulo(consulta.get("precoMinimo"))
                || !numeroOuNulo(consulta.get("precoMaximo"))) {
            throw new IllegalArgumentException("Consulta de catalogo invalida");
        }
    }

    private boolean textoOuNulo(JsonNode node) {
        return node.isNull() || node.isTextual();
    }

    private boolean numeroOuNulo(JsonNode node) {
        return node.isNull() || node.isNumber();
    }

    private Set<String> campos(JsonNode node) {
        Set<String> resultado = new HashSet<>();
        node.fieldNames().forEachRemaining(resultado::add);
        return resultado;
    }

    private String removerMarkdown(String conteudo) {
        if (conteudo == null) {
            throw new IllegalArgumentException("Resposta da IA vazia");
        }
        String texto = conteudo.trim();
        if (texto.startsWith("```")) {
            texto = texto.replaceFirst("^```json\\s*", "")
                    .replaceFirst("^```\\s*", "")
                    .replaceFirst("\\s*```$", "");
        }
        return texto.trim();
    }

    private InterpretacaoIaResponseDTO respostaRefinamentoInvalido(
            RespostaInterpretacaoIaInterna planejamento
    ) {
        return substituirResposta(
                planejamento,
                "Preciso de uma categoria, nome de produto ou faixa de preco valida para consultar o catalogo.",
                false,
                null
        );
    }

    private InterpretacaoIaResponseDTO respostaSemResultado(RespostaInterpretacaoIaInterna planejamento) {
        return substituirResposta(
                planejamento,
                "Nao localizei itens para esse criterio no catalogo. Vou encaminhar para a equipe confirmar.",
                true,
                "Item nao localizado na consulta limitada do catalogo."
        );
    }

    private InterpretacaoIaResponseDTO respostaCatalogoIndisponivel(
            RespostaInterpretacaoIaInterna planejamento
    ) {
        return substituirResposta(
                planejamento,
                "Nao consegui consultar o catalogo agora. Vou encaminhar para a equipe confirmar.",
                true,
                "Catalogo indisponivel para consulta."
        );
    }

    private InterpretacaoIaResponseDTO respostaContextoCatalogoVazio(
            RespostaInterpretacaoIaInterna planejamento
    ) {
        return substituirResposta(
                planejamento,
                "Nao consegui preparar os dados do catalogo dentro dos limites seguros. Informe uma categoria ou um produto mais especifico.",
                false,
                null
        );
    }

    private InterpretacaoIaResponseDTO substituirResposta(
            RespostaInterpretacaoIaInterna planejamento,
            String resposta,
            boolean humano,
            String motivo
    ) {
        return new InterpretacaoIaResponseDTO(
                planejamento.tipoSolicitacao(),
                planejamento.categoria(),
                resposta,
                humano,
                motivo,
                planejamento.confianca()
        ).normalizado();
    }

    private InterpretacaoIaResponseDTO substituirRespostaCatalogo(
            RespostaInterpretacaoIaInterna planejamento,
            String resposta,
            boolean humano,
            String motivo
    ) {
        return new InterpretacaoIaResponseDTO(
                TipoSolicitacaoEnum.COMPRA_PRODUTO,
                CategoriaAtendimentoEnum.ATENDIMENTO_COMERCIAL,
                resposta,
                humano,
                motivo,
                planejamento.confianca()
        ).normalizado();
    }

    private InterpretacaoIaResponseDTO normalizarRespostaCatalogo(
            RespostaInterpretacaoIaInterna resposta
    ) {
        return new InterpretacaoIaResponseDTO(
                TipoSolicitacaoEnum.COMPRA_PRODUTO,
                CategoriaAtendimentoEnum.ATENDIMENTO_COMERCIAL,
                resposta.respostaGerada(),
                resposta.necessitaAtendimentoHumano(),
                resposta.motivoEncaminhamento(),
                resposta.confianca()
        ).normalizado();
    }

    private InterpretacaoIaResponseDTO respostaDeterministicaProdutos(
            RespostaInterpretacaoIaInterna planejamento,
            List<ProdutoCatalogo> produtos
    ) {
        String itens = produtos.stream()
                .map(produto -> nomeProdutoSeguro(produto.produto()) + " por R$ " + formatarPreco(produto.precoAtual()))
                .reduce((primeiro, seguinte) -> primeiro + "; " + seguinte)
                .orElse("");
        return substituirRespostaCatalogo(
                planejamento,
                "Encontrei no catalogo: " + itens
                        + ". Informe qual produto deseja, a quantidade, a forma de pagamento e se prefere entrega ou retirada.",
                false,
                null
        );
    }

    private boolean respostaUsaResultados(String resposta, List<ProdutoCatalogo> produtos) {
        String texto = normalizar(resposta);
        return produtos.stream().anyMatch(produto -> {
            String nome = normalizar(nomeProdutoSeguro(produto.produto()));
            String preco = formatarPreco(produto.precoAtual());
            return texto.contains(nome)
                    && (texto.contains(normalizar(preco)) || texto.contains(produto.precoAtual().toPlainString()));
        });
    }

    private String formatarPreco(BigDecimal preco) {
        return preco.setScale(2).toPlainString().replace('.', ',');
    }

    private String nomeProdutoSeguro(String nome) {
        String seguro = nome == null ? "Produto" : nome.replaceAll("[\\p{Cntrl}]", " ")
                .replace("RESULTADO_CATALOGO_NAO_CONFIAVEL_INICIO", "")
                .replace("RESULTADO_CATALOGO_NAO_CONFIAVEL_FIM", "")
                .trim();
        if (seguro.isBlank()) {
            return "Produto";
        }
        return seguro.length() <= 160 ? seguro : seguro.substring(0, 160);
    }

    private String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
