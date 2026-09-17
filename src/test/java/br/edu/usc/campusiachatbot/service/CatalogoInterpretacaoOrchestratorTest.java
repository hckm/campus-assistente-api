package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.config.CatalogoConsultaProperties;
import br.edu.usc.campusiachatbot.config.EstabelecimentoProperties;
import br.edu.usc.campusiachatbot.domain.ProdutoCatalogo;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.EnderecoEnriquecidoDTO;
import br.edu.usc.campusiachatbot.dto.InterpretacaoIaResponseDTO;
import br.edu.usc.campusiachatbot.enums.CategoriaAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.TipoSolicitacaoEnum;
import br.edu.usc.campusiachatbot.store.CatalogoStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CatalogoInterpretacaoOrchestratorTest {

    private CatalogoStore catalogoStore;
    private CatalogoInterpretacaoOrchestrator orchestrator;
    private PromptBuilderService promptBuilder;

    @BeforeEach
    void configurar() {
        catalogoStore = mock(CatalogoStore.class);
        promptBuilder = new PromptBuilderService(() -> new EstabelecimentoProperties(
                "Farmacia Teste",
                "farmacia",
                "08:00 as 18:00",
                "Rua Teste",
                "Pix",
                null,
                "Correio",
                List.of("Iacanga"),
                "SP",
                "14999999999",
                "https://catalogo.example"
        ));
        orchestrator = new CatalogoInterpretacaoOrchestrator(
                promptBuilder,
                new LocalInterpretacaoService(),
                catalogoStore,
                new CatalogoConsultaProperties(8, 6000, 12000),
                new ObjectMapper()
        );
    }

    @Test
    void mensagemAdministrativaUsaUmaInferenciaENaoConsultaCatalogo() {
        InferenciaSequencial inferencia = new InferenciaSequencial(resposta(null, false, "Horario informado."));

        InterpretacaoIaResponseDTO resposta = interpretar("Qual o horario?", inferencia);

        assertThat(resposta.respostaGerada()).isEqualTo("Horario informado.");
        assertThat(inferencia.chamadas()).isEqualTo(1);
        verifyNoInteractions(catalogoStore);
    }

    @Test
    void classificacaoAdministrativaIgnoraConsultaSolicitadaPeloProvedor() {
        String respostaIa = """
                {
                  "tipoSolicitacao": "ENTREGA",
                  "categoria": "ATENDIMENTO_ADMINISTRATIVO",
                  "respostaGerada": "Informe o CEP.",
                  "necessitaAtendimentoHumano": false,
                  "motivoEncaminhamento": null,
                  "confianca": 90,
                  "solicitarCategorias": false,
                  "consultaCatalogo": {"operacao":"BUSCAR_PRODUTO","termo":"Serum","categoria":null,"precoMinimo":null,"precoMaximo":null}
                }
                """;
        InferenciaSequencial inferencia = new InferenciaSequencial(respostaIa);

        InterpretacaoIaResponseDTO resposta = interpretar("Qual o prazo de entrega?", inferencia);

        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.ENTREGA);
        assertThat(inferencia.chamadas()).isEqualTo(1);
        verifyNoInteractions(catalogoStore);
    }

    @Test
    void pedidoGenericoListaCategoriasSemCarregarProdutos() {
        when(catalogoStore.listarCategoriasLimitadas(8)).thenReturn(List.of("CAPILAR", "FACIAL"));
        InferenciaSequencial inferencia = new InferenciaSequencial(respostaClassificada(
                "OUTROS", "OUTROS", null, false, "Nao sei."));

        InterpretacaoIaResponseDTO resposta = interpretar("Quais produtos voces tem?", inferencia);

        assertThat(resposta.respostaGerada()).contains("CAPILAR", "FACIAL", "Qual categoria");
        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.COMPRA_PRODUTO);
        assertThat(resposta.categoria()).isEqualTo(CategoriaAtendimentoEnum.ATENDIMENTO_COMERCIAL);
        assertThat(inferencia.chamadas()).isEqualTo(1);
        verify(catalogoStore).listarCategoriasLimitadas(8);
        verify(catalogoStore, never()).listarTodosOrdenadosPorCodigo();
        verify(catalogoStore, never()).listarPorCategoriaLimitada(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void categoriaExecutaBuscaLimitadaESegundaInferencia() {
        ProdutoCatalogo produto = produto("FACIAL", "Serum", "79.90");
        when(catalogoStore.listarPorCategoriaLimitada("FACIAL", 8)).thenReturn(List.of(produto));
        InferenciaSequencial inferencia = new InferenciaSequencial(
                resposta(consultaCategoria("FACIAL"), false, "Consultando."),
                resposta(null, false, "O Serum custa R$ 79,90.")
        );

        InterpretacaoIaResponseDTO resposta = interpretar("Mostre produtos faciais", inferencia);

        assertThat(resposta.respostaGerada()).isEqualTo("O Serum custa R$ 79,90.");
        assertThat(inferencia.chamadas()).isEqualTo(2);
        assertThat(inferencia.contents().get(1)).hasSameSizeAs(inferencia.contents().get(0));
        assertThat(inferencia.contents().get(1).toString())
                .contains("RESULTADO_CATALOGO_NAO_CONFIAVEL_INICIO", "produto=Serum", "precoAtual=R$ 79.90");
        verify(catalogoStore).listarPorCategoriaLimitada("FACIAL", 8);
        verify(catalogoStore, never()).listarTodosOrdenadosPorCodigo();
    }

    @Test
    void nomeExecutaSomenteBuscaLimitadaPeloTermo() {
        when(catalogoStore.pesquisarPorProdutoLimitado("Serum", 8))
                .thenReturn(List.of(produto("FACIAL", "Serum", "79.90")));
        InferenciaSequencial inferencia = new InferenciaSequencial(
                resposta(consultaProduto("Serum"), false, "Consultando."),
                respostaClassificada("OUTROS", "OUTROS", null, false, "O Serum custa R$ 79,90.")
        );

        InterpretacaoIaResponseDTO resposta = interpretar("Tem Serum?", inferencia);

        verify(catalogoStore).pesquisarPorProdutoLimitado("Serum", 8);
        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.COMPRA_PRODUTO);
        assertThat(resposta.categoria()).isEqualTo(CategoriaAtendimentoEnum.ATENDIMENTO_COMERCIAL);
        assertThat(resposta.respostaGerada()).contains("Serum", "79,90");
        assertThat(inferencia.chamadas()).isEqualTo(2);
    }

    @Test
    void faixaExecutaBuscaLimitadaComValoresValidados() {
        when(catalogoStore.listarPorFaixaDePrecoLimitada(
                new BigDecimal("10.0"), new BigDecimal("20.0"), 8))
                .thenReturn(List.of(produto("CAPILAR", "Shampoo", "15.00")));
        InferenciaSequencial inferencia = new InferenciaSequencial(
                resposta(consultaFaixa("10.00", "20.00"), false, "Consultando."),
                resposta(null, false, "Produto localizado.")
        );

        InterpretacaoIaResponseDTO resposta = interpretar("Produtos de 10 a 20 reais", inferencia);

        verify(catalogoStore).listarPorFaixaDePrecoLimitada(
                new BigDecimal("10.0"), new BigDecimal("20.0"), 8);
        assertThat(resposta.respostaGerada()).contains("Shampoo", "15,00");
        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.COMPRA_PRODUTO);
        assertThat(inferencia.chamadas()).isEqualTo(2);
    }

    @Test
    void consultaVaziaNaoAcessaCatalogoNemFazSegundaInferencia() {
        InferenciaSequencial inferencia = new InferenciaSequencial(
                resposta(consultaProduto(""), false, "Consultando.")
        );

        InterpretacaoIaResponseDTO resposta = interpretar("Tem algum produto?", inferencia);

        assertThat(resposta.respostaGerada()).contains("categoria, nome de produto ou faixa de preco valida");
        assertThat(inferencia.chamadas()).isEqualTo(1);
        verifyNoInteractions(catalogoStore);
    }

    @Test
    void faixaComFracaoDeCentavoNaoAcessaCatalogo() {
        InferenciaSequencial inferencia = new InferenciaSequencial(
                resposta(consultaFaixa("10.001", "20.00"), false, "Consultando.")
        );

        InterpretacaoIaResponseDTO resposta = interpretar("Produtos nessa faixa", inferencia);

        assertThat(resposta.respostaGerada()).contains("faixa de preco valida");
        assertThat(inferencia.chamadas()).isEqualTo(1);
        verifyNoInteractions(catalogoStore);
    }

    @Test
    void buscaSemResultadoNaoFazSegundaInferencia() {
        when(catalogoStore.pesquisarPorProdutoLimitado("Inexistente", 8)).thenReturn(List.of());
        InferenciaSequencial inferencia = new InferenciaSequencial(
                resposta(consultaProduto("Inexistente"), false, "Consultando.")
        );

        InterpretacaoIaResponseDTO resposta = interpretar("Tem Inexistente?", inferencia);

        assertThat(resposta.respostaGerada()).contains("Nao localizei itens");
        assertThat(resposta.necessitaAtendimentoHumano()).isTrue();
        assertThat(inferencia.chamadas()).isEqualTo(1);
    }

    @Test
    void segundaInferenciaNaoPodeEncadearTerceiraConsulta() {
        when(catalogoStore.pesquisarPorProdutoLimitado("Serum", 8))
                .thenReturn(List.of(produto("FACIAL", "Serum", "79.90")));
        InferenciaSequencial inferencia = new InferenciaSequencial(
                resposta(consultaProduto("Serum"), false, "Consultando."),
                resposta(consultaProduto("Outro"), false, "Consultando novamente."),
                resposta(null, false, "Nao deveria ser usada.")
        );

        InterpretacaoIaResponseDTO resposta = interpretar("Tem Serum?", inferencia);

        assertThat(resposta.respostaGerada()).contains("Serum", "79,90");
        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.COMPRA_PRODUTO);
        assertThat(inferencia.chamadas()).isEqualTo(2);
        verify(catalogoStore).pesquisarPorProdutoLimitado("Serum", 8);
        verify(catalogoStore, never()).pesquisarPorProdutoLimitado("Outro", 8);
    }

    @Test
    void limitaDefensivamenteItensRecebidosDoStore() {
        List<ProdutoCatalogo> produtos = java.util.stream.IntStream.range(0, 12)
                .mapToObj(indice -> new ProdutoCatalogo(
                        "produto:" + indice,
                        null,
                        indice,
                        "FACIAL",
                        "Produto " + indice,
                        "Descricao",
                        new BigDecimal("10.00"),
                        null,
                        null
                ))
                .toList();
        when(catalogoStore.listarPorCategoriaLimitada("FACIAL", 8)).thenReturn(produtos);
        InferenciaSequencial inferencia = new InferenciaSequencial(
                resposta(consultaCategoria("FACIAL"), false, "Consultando."),
                resposta(null, false, "Produtos localizados.")
        );

        interpretar("Mostre produtos faciais", inferencia);

        String segundaEntrada = inferencia.contents().get(1).toString();
        assertThat(segundaEntrada).contains("produto=Produto 0", "produto=Produto 7");
        assertThat(segundaEntrada).doesNotContain("produto=Produto 8", "produto=Produto 11");
    }

    @Test
    void naoFazSegundaInferenciaQuandoNenhumProdutoCabeNoContexto() {
        ProdutoCatalogo produtoGrande = new ProdutoCatalogo(
                "produto:1",
                null,
                1,
                "€".repeat(60),
                "€".repeat(160),
                "€".repeat(300),
                new BigDecimal("10.00"),
                null,
                "€".repeat(255)
        );
        when(catalogoStore.pesquisarPorProdutoLimitado("Produto", 8)).thenReturn(List.of(produtoGrande));
        CatalogoInterpretacaoOrchestrator orchestratorRestrito = new CatalogoInterpretacaoOrchestrator(
                promptBuilder,
                new LocalInterpretacaoService(),
                catalogoStore,
                new CatalogoConsultaProperties(8, 1000, 2000),
                new ObjectMapper()
        );
        InferenciaSequencial inferencia = new InferenciaSequencial(
                resposta(consultaProduto("Produto"), false, "Consultando.")
        );

        InterpretacaoIaResponseDTO resposta = orchestratorRestrito.interpretar(
                new ChatbotRequestDTO("14999999999", "Cliente", "Tem Produto?", null),
                EnderecoEnriquecidoDTO.vazio(),
                List.of(),
                inferencia,
                "TESTE"
        );

        assertThat(resposta.respostaGerada()).contains("dentro dos limites seguros");
        assertThat(inferencia.chamadas()).isEqualTo(1);
        verify(catalogoStore).pesquisarPorProdutoLimitado("Produto", 8);
    }

    @Test
    void falhaNaPrimeiraInferenciaUsaClassificacaoLocalExistente() {
        InferenciaIa inferencia = contents -> {
            throw new IllegalStateException("falha simulada");
        };

        InterpretacaoIaResponseDTO resposta = orchestrator.interpretar(
                new ChatbotRequestDTO("14999999999", "Cliente", "Qual a dose?", null),
                EnderecoEnriquecidoDTO.vazio(),
                List.of(),
                inferencia,
                "TESTE"
        );

        assertThat(resposta.tipoSolicitacao()).isEqualTo(TipoSolicitacaoEnum.DUVIDA_FARMACEUTICA);
        assertThat(resposta.necessitaAtendimentoHumano()).isTrue();
        verifyNoInteractions(catalogoStore);
    }

    private InterpretacaoIaResponseDTO interpretar(String mensagem, InferenciaSequencial inferencia) {
        return orchestrator.interpretar(
                new ChatbotRequestDTO("14999999999", "Cliente", mensagem, null),
                EnderecoEnriquecidoDTO.vazio(),
                List.of(),
                inferencia,
                "TESTE"
        );
    }

    private String resposta(String consulta, boolean solicitarCategorias, String texto) {
        return respostaClassificada(
                "COMPRA_PRODUTO",
                "ATENDIMENTO_COMERCIAL",
                consulta,
                solicitarCategorias,
                texto
        );
    }

    private String respostaClassificada(
            String tipo,
            String categoria,
            String consulta,
            boolean solicitarCategorias,
            String texto
    ) {
        return """
                {
                  "tipoSolicitacao": "%s",
                  "categoria": "%s",
                  "respostaGerada": "%s",
                  "necessitaAtendimentoHumano": false,
                  "motivoEncaminhamento": null,
                  "confianca": 90,
                  "solicitarCategorias": %s,
                  "consultaCatalogo": %s
                }
                """.formatted(tipo, categoria, texto, solicitarCategorias, consulta == null ? "null" : consulta);
    }

    private String consultaCategoria(String categoria) {
        return """
                {"operacao":"BUSCAR_CATEGORIA","termo":null,"categoria":"%s","precoMinimo":null,"precoMaximo":null}
                """.formatted(categoria).trim();
    }

    private String consultaProduto(String termo) {
        return """
                {"operacao":"BUSCAR_PRODUTO","termo":"%s","categoria":null,"precoMinimo":null,"precoMaximo":null}
                """.formatted(termo).trim();
    }

    private String consultaFaixa(String minimo, String maximo) {
        return """
                {"operacao":"BUSCAR_FAIXA_PRECO","termo":null,"categoria":null,"precoMinimo":%s,"precoMaximo":%s}
                """.formatted(minimo, maximo).trim();
    }

    private ProdutoCatalogo produto(String categoria, String nome, String preco) {
        return new ProdutoCatalogo(
                "produto:1",
                null,
                1,
                categoria,
                nome,
                "Descricao segura",
                new BigDecimal(preco),
                null,
                "https://catalogo.example/categoria"
        );
    }

    private static final class InferenciaSequencial implements InferenciaIa {

        private final Queue<String> respostas;
        private final List<List<Map<String, Object>>> contents = new ArrayList<>();

        private InferenciaSequencial(String... respostas) {
            this.respostas = new ArrayDeque<>(List.of(respostas));
        }

        @Override
        public String executar(List<Map<String, Object>> contents) {
            this.contents.add(contents);
            return respostas.remove();
        }

        private int chamadas() {
            return contents.size();
        }

        private List<List<Map<String, Object>>> contents() {
            return contents;
        }
    }
}
