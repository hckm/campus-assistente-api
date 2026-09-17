package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.client.CepLookupClient;
import br.edu.usc.campusiachatbot.config.ChatbotMessagesProperties;
import br.edu.usc.campusiachatbot.config.ChatbotSessionProperties;
import br.edu.usc.campusiachatbot.config.EstabelecimentoProperties;
import br.edu.usc.campusiachatbot.domain.Atendimento;
import br.edu.usc.campusiachatbot.domain.InicioInteracao;
import br.edu.usc.campusiachatbot.domain.MensagemConversa;
import br.edu.usc.campusiachatbot.dto.CepLookupResponseDTO;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.ChatbotResponseDTO;
import br.edu.usc.campusiachatbot.dto.EnderecoEnriquecidoDTO;
import br.edu.usc.campusiachatbot.dto.InterpretacaoIaResponseDTO;
import br.edu.usc.campusiachatbot.enums.CategoriaAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.DirecaoMensagemEnum;
import br.edu.usc.campusiachatbot.enums.OrigemMensagemEnum;
import br.edu.usc.campusiachatbot.enums.StatusAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.TipoSolicitacaoEnum;
import br.edu.usc.campusiachatbot.repository.ClienteChave;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotService {

    private final InterpretacaoIaService interpretacaoIaService;
    private final AtendimentoService atendimentoService;
    private final EstabelecimentoComercialProvider estabelecimentoComercialProvider;
    private final ChatbotMessagesProperties messagesProperties;
    private final ChatbotSessionProperties sessionProperties;
    private final CepLookupClient cepLookupClient;
    private final EnderecoEnrichmentService enderecoEnrichmentService;

    public ChatbotResponseDTO processarMensagem(ChatbotRequestDTO request, OrigemMensagemEnum origemPadrao) {
        Optional<Atendimento> conversaAtiva = atendimentoService.buscarConversaAtiva(
                request.telefoneCliente(), sessionProperties.janelaInatividadeMinutos());
        Atendimento atendimento = conversaAtiva.isPresent()
                ? atendimentoService.adicionarMensagemCliente(conversaAtiva.get(), request)
                : atendimentoService.criarRecebido(request, origemPadrao);
        try {
            List<MensagemConversa> historico = atendimentoService.buscarHistoricoMensagens(
                    atendimento.id(), sessionProperties.maxHistoricoMensagens());
            EnderecoEnriquecidoDTO enderecoEnriquecido = enderecoEnrichmentService.enriquecer(request.mensagem());
            InterpretacaoIaResponseDTO resposta = interpretacaoIaService.interpretarMensagem(
                    request, enderecoEnriquecido, historico
            );
            InterpretacaoIaResponseDTO respostaSegura = aplicarTerminologiaCliente(aplicarRegrasDeSeguranca(
                    request.mensagem(), resposta, enderecoEnriquecido, historico
            ));
            StatusAtendimentoEnum status = respostaSegura.necessitaAtendimentoHumano()
                    ? StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA
                    : StatusAtendimentoEnum.PROCESSADO;
            Atendimento atualizado = atendimentoService.atualizarComResposta(atendimento, respostaSegura, status);
            log.info(
                    "Atendimento {} processado; origem={}; clienteRef={}; tipo={}; status={}",
                    atualizado.id(), atualizado.origem(), referenciaCliente(atualizado), atualizado.tipoSolicitacao(), atualizado.status()
            );
            return aplicarTerminologiaCliente(atendimentoService.toResponseDTO(atualizado));
        } catch (RuntimeException exception) {
            try {
                atendimentoService.marcarErro(atendimento);
            } catch (RuntimeException erroPersistencia) {
                log.warn("Falha ao marcar erro do atendimento {}; origem={}; clienteRef={}; tipoFalhaPersistencia={}",
                        atendimento.id(), atendimento.origem(), referenciaCliente(atendimento), erroPersistencia.getClass().getName());
            }
            log.error("Erro ao processar atendimento {}; origem={}; clienteRef={}",
                    atendimento.id(), atendimento.origem(), referenciaCliente(atendimento), exception);
            throw exception;
        }
    }

    public ChatbotResponseDTO processarMensagem(
            ChatbotRequestDTO request,
            OrigemMensagemEnum origemPadrao,
            String idempotencyKey
    ) {
        InicioInteracao interacao = atendimentoService.iniciarInteracao(request, origemPadrao, idempotencyKey);
        Atendimento atendimento = interacao.atendimento();
        if (interacao.repetida()) {
            return aplicarTerminologiaCliente(atendimentoService.toResponseDTO(atendimento));
        }

        try {
            List<MensagemConversa> historico = atendimentoService.buscarHistoricoInteracao(
                    interacao, sessionProperties.maxHistoricoMensagens());

            EnderecoEnriquecidoDTO enderecoEnriquecido = enderecoEnrichmentService.enriquecer(request.mensagem());
            InterpretacaoIaResponseDTO resposta = interpretacaoIaService.interpretarMensagem(request, enderecoEnriquecido, historico);
            InterpretacaoIaResponseDTO respostaSegura = aplicarTerminologiaCliente(aplicarRegrasDeSeguranca(
                    request.mensagem(),
                    resposta,
                    enderecoEnriquecido,
                    historico
            ));
            StatusAtendimentoEnum status = respostaSegura.necessitaAtendimentoHumano()
                    ? StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA
                    : StatusAtendimentoEnum.PROCESSADO;

            Atendimento atualizado = atendimentoService.atualizarComRespostaInteracao(interacao, respostaSegura, status);
            log.info(
                    "Atendimento {} processado; origem={}; clienteRef={}; tipo={}; status={}",
                    atualizado.id(),
                    atualizado.origem(),
                    referenciaCliente(atualizado),
                    atualizado.tipoSolicitacao(),
                    atualizado.status()
            );
            return aplicarTerminologiaCliente(atendimentoService.toResponseDTO(atualizado));
        } catch (RuntimeException exception) {
            try {
                atendimentoService.marcarErroInteracao(interacao);
            } catch (RuntimeException erroPersistencia) {
                log.warn("Falha ao marcar erro do atendimento {}; origem={}; clienteRef={}; tipoFalhaPersistencia={}",
                        atendimento.id(), atendimento.origem(), referenciaCliente(atendimento), erroPersistencia.getClass().getName());
            }
            log.error("Erro ao processar atendimento {}; origem={}; clienteRef={}",
                    atendimento.id(), atendimento.origem(), referenciaCliente(atendimento), exception);
            throw exception;
        }
    }

    private InterpretacaoIaResponseDTO aplicarRegrasDeSeguranca(
            String mensagem,
            InterpretacaoIaResponseDTO resposta,
            EnderecoEnriquecidoDTO enderecoEnriquecido,
            List<MensagemConversa> historico
    ) {
        InterpretacaoIaResponseDTO normalizada = resposta.normalizado();
        String texto = normalizar(mensagem);

        if (mensagemPedeOpiniaoSobreProduto(texto)) {
            return responderOpiniaoSobreProduto(mensagem, historico, normalizada.confianca());
        }

        if (mensagemExigeAtendimentoHumano(texto)) {
            return encaminharParaHumano(normalizada);
        }

        boolean compraEmAndamento = normalizada.tipoSolicitacao() == TipoSolicitacaoEnum.COMPRA_PRODUTO
                || historicoIndicaCompra(historico, mensagem);
        if (compraEmAndamento) {
            InterpretacaoIaResponseDTO pedidoCompleto = resumirPedidoCompleto(normalizada, historico);
            if (pedidoCompleto != null) {
                return pedidoCompleto;
            }
        }

        InterpretacaoIaResponseDTO respostaAdministrativa = responderAdministrativoQuandoPossivel(
                texto,
                enderecoEnriquecido,
                compraEmAndamento
        );
        if (respostaAdministrativa != null) {
            return respostaAdministrativa;
        }

        if (respostaIndicaAtendimentoHumano(normalizada)) {
            return encaminharParaHumano(normalizada);
        }

        return normalizada;
    }

    private InterpretacaoIaResponseDTO encaminharParaHumano(InterpretacaoIaResponseDTO resposta) {
        String motivo = resposta.motivoEncaminhamento();
        if (motivo == null || motivo.isBlank()) {
            motivo = motivoPadrao(resposta.tipoSolicitacao());
        }

        String respostaGerada = resposta.respostaGerada();
        if (resposta.categoria() == CategoriaAtendimentoEnum.ATENDIMENTO_FARMACEUTICO
                || resposta.tipoSolicitacao() == TipoSolicitacaoEnum.DUVIDA_FARMACEUTICA) {
            respostaGerada = "Essa orientacao depende de uma avaliacao individual. A equipe farmaceutica pode analisar seu caso com seguranca. Posso encaminhar sua duvida para um farmaceutico ou ajudar com informacoes administrativas do produto.";
        }

        return new InterpretacaoIaResponseDTO(
                resposta.tipoSolicitacao(),
                resposta.categoria(),
                respostaGerada,
                true,
                motivo,
                resposta.confianca()
        );
    }

    private boolean respostaIndicaAtendimentoHumano(InterpretacaoIaResponseDTO resposta) {
        return resposta.necessitaAtendimentoHumano()
                || resposta.tipoSolicitacao() == TipoSolicitacaoEnum.DUVIDA_FARMACEUTICA
                || resposta.tipoSolicitacao() == TipoSolicitacaoEnum.ORCAMENTO_FORMULA
                || resposta.categoria() == CategoriaAtendimentoEnum.ATENDIMENTO_FARMACEUTICO;
    }

    private boolean mensagemExigeAtendimentoHumano(String texto) {
        return contem(texto,
                "dose",
                "dosagem",
                "tomar",
                "posologia",
                "efeito colateral",
                "reacao",
                "interacao",
                "gravidez",
                "gravida",
                "amament",
                "crianca",
                "bebe",
                "sintoma",
                "substituir",
                "contraindic",
                "receita",
                "formula",
                "orcamento",
                "reclam"
        );
    }

    private boolean mensagemPedeOpiniaoSobreProduto(String texto) {
        if (contem(texto,
                "entrega", "frete", "cep", "motoboy", "correio", "transportadora",
                "horario", "funcionamento", "pagamento", "pix", "cartao", "boleto",
                "status", "andamento", "pedido")) {
            return false;
        }
        return contem(texto,
                "e uma boa",
                "e bom",
                "e boa",
                "nao acha",
                "o que acha",
                "voce acha",
                "recomenda",
                "vale a pena",
                "funciona",
                "serve para mim",
                "e seguro",
                "e segura",
                "e adequado",
                "e adequada"
        );
    }

    private InterpretacaoIaResponseDTO responderOpiniaoSobreProduto(
            String mensagem,
            List<MensagemConversa> historico,
            Double confianca
    ) {
        String produto = extrairProdutoDaOpiniao(mensagem);
        String preco = localizarPrecoNoHistorico(produto, historico);
        boolean apareceuNoCatalogo = historicoBotContem(produto, historico);
        String contextoCatalogo;
        if (apareceuNoCatalogo && preco != null) {
            contextoCatalogo = " Na consulta do catalogo desta conversa, ele apareceu por R$ " + preco + ".";
        } else if (apareceuNoCatalogo) {
            contextoCatalogo = " Ele apareceu na consulta do catalogo desta conversa.";
        } else {
            contextoCatalogo = " Posso consultar os dados disponiveis no catalogo antes de voce decidir.";
        }
        String resposta = "Entendo que " + produto + " chamou sua atencao." + contextoCatalogo
                + " Nao posso afirmar que ele seja eficaz, seguro ou adequado para voce, pois a escolha depende "
                + "do seu perfil, objetivo, condicoes de saude e de outros produtos ou medicamentos que utilize. "
                + "A equipe farmaceutica pode orientar com seguranca. Posso consultar mais detalhes do produto, "
                + "ajudar com a compra ou colocar voce em contato com a equipe farmaceutica.";
        return new InterpretacaoIaResponseDTO(
                TipoSolicitacaoEnum.DUVIDA_FARMACEUTICA,
                CategoriaAtendimentoEnum.ATENDIMENTO_FARMACEUTICO,
                resposta,
                true,
                "Escolha de produto requer orientacao da equipe farmaceutica.",
                confianca
        ).normalizado();
    }

    private String extrairProdutoDaOpiniao(String mensagem) {
        String candidato = normalizar(mensagem)
                .replaceAll("[^a-z0-9 -]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .replaceFirst("^(?:o que (?:voce )?acha d[oa]|(?:voce )?recomenda|acha que)\\s+", "")
                .replaceFirst("^(?:o|a|os|as)\\s+", "");
        String[] marcadores = {
                " e uma boa", " e bom", " e boa", " nao acha", " o que acha", " voce acha",
                " recomenda", " vale a pena", " funciona", " serve para mim", " e seguro", " e segura",
                " e adequado", " e adequada"
        };
        int fim = candidato.length();
        for (String marcador : marcadores) {
            int indice = candidato.indexOf(marcador);
            if (indice >= 0 && indice < fim) {
                fim = indice;
            }
        }
        String produto = candidato.substring(0, fim).trim();
        if (produto.isBlank()) {
            return "esse produto";
        }
        String limitado = produto.length() <= 160 ? produto : produto.substring(0, 160).trim();
        return Character.toUpperCase(limitado.charAt(0)) + limitado.substring(1);
    }

    private String localizarPrecoNoHistorico(String produto, List<MensagemConversa> historico) {
        if (historico == null || produto.equals("esse produto")) {
            return null;
        }
        String termo = normalizar(produto);
        Pattern preco = Pattern.compile("(?i)R\\$\\s*(\\d+(?:[.,]\\d{1,2})?)");
        for (MensagemConversa mensagem : historico) {
            if (mensagem.direcao() != DirecaoMensagemEnum.BOT || mensagem.conteudo() == null) {
                continue;
            }
            for (String trecho : mensagem.conteudo().split("[;\\r\\n]+")) {
                if (!normalizar(trecho).contains(termo)) {
                    continue;
                }
                Matcher matcher = preco.matcher(trecho);
                if (matcher.find()) {
                    return matcher.group(1).replace('.', ',');
                }
            }
        }
        return null;
    }

    private boolean historicoBotContem(String produto, List<MensagemConversa> historico) {
        if (historico == null || produto.equals("esse produto")) {
            return false;
        }
        String termo = normalizar(produto);
        return historico.stream()
                .filter(mensagem -> mensagem.direcao() == DirecaoMensagemEnum.BOT)
                .map(MensagemConversa::conteudo)
                .filter(conteudo -> conteudo != null)
                .map(this::normalizar)
                .anyMatch(conteudo -> conteudo.contains(termo));
    }

    private InterpretacaoIaResponseDTO responderAdministrativoQuandoPossivel(
            String texto,
            EnderecoEnriquecidoDTO enderecoEnriquecido,
            boolean compraEmAndamento
    ) {
        if (contem(texto, "horario", "funcionamento", "abre", "fecha")) {
            if (estabelecimento().hasHorarioFuncionamento()) {
                return new InterpretacaoIaResponseDTO(
                        TipoSolicitacaoEnum.HORARIO_FUNCIONAMENTO,
                        CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                        messagesProperties.horarioInformado(
                                estabelecimento().nomeOuPadrao(),
                                estabelecimento().horarioFuncionamento().trim()
                        ),
                        false,
                        null,
                        100.0
                );
            }

            return new InterpretacaoIaResponseDTO(
                    TipoSolicitacaoEnum.HORARIO_FUNCIONAMENTO,
                    CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                    messagesProperties.horarioNaoConfigurado(),
                    true,
                    "Horario de funcionamento nao configurado para o estabelecimento.",
                    100.0
            );
        }

        if (!compraEmAndamento && contem(texto, "parcel", "vezes")) {
            if (estabelecimento().hasCondicoesParcelamento()) {
                return new InterpretacaoIaResponseDTO(
                        TipoSolicitacaoEnum.FORMAS_PAGAMENTO,
                        CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                        estabelecimento().condicoesParcelamento().trim(),
                        false,
                        null,
                        100.0
                );
            }

            return new InterpretacaoIaResponseDTO(
                    TipoSolicitacaoEnum.FORMAS_PAGAMENTO,
                    CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                    "Aceitamos cartao, mas a quantidade de parcelas e as condicoes precisam ser confirmadas pela equipe.",
                    true,
                    "Condicoes de parcelamento nao configuradas para o estabelecimento.",
                    100.0
            );
        }

        if (!compraEmAndamento && contem(texto, "pagamento", "cartao", "pix", "dinheiro", "boleto")) {
            if (estabelecimento().hasFormasPagamento()) {
                return new InterpretacaoIaResponseDTO(
                        TipoSolicitacaoEnum.FORMAS_PAGAMENTO,
                        CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                        messagesProperties.pagamentoInformado(
                                estabelecimento().nomeOuPadrao(),
                                estabelecimento().formasPagamento().trim()
                        ),
                        false,
                        null,
                        100.0
                );
            }

            return new InterpretacaoIaResponseDTO(
                    TipoSolicitacaoEnum.FORMAS_PAGAMENTO,
                    CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                    messagesProperties.pagamentoNaoConfigurado(),
                    true,
                    "Formas de pagamento nao configuradas para o estabelecimento.",
                    100.0
            );
        }

        if (contem(texto, "endereco", "localizacao", "onde fica")) {
            if (estabelecimento().hasEndereco()) {
                return new InterpretacaoIaResponseDTO(
                        TipoSolicitacaoEnum.DUVIDA_ADMINISTRATIVA,
                        CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                        messagesProperties.enderecoInformado(
                                estabelecimento().nomeOuPadrao(),
                                estabelecimento().endereco().trim()
                        ),
                        false,
                        null,
                        100.0
                );
            }

            return new InterpretacaoIaResponseDTO(
                    TipoSolicitacaoEnum.DUVIDA_ADMINISTRATIVA,
                    CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                    messagesProperties.enderecoNaoConfigurado(),
                    true,
                    "Endereco nao configurado para o estabelecimento.",
                    100.0
            );
        }

        if (contem(texto, "entrega", "delivery", "cep", "correio", "transportadora", "motoboy")) {
            if (contem(texto, "correio", "transportadora") && !contem(texto, "motoboy")) {
                return new InterpretacaoIaResponseDTO(
                        TipoSolicitacaoEnum.ENTREGA,
                        CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                        "Sim! Realizamos entregas via Correio e Transportadora para todo o Brasil. Para calcular o frete e confirmar o pedido, informe seu endereco completo com CEP.",
                        false,
                        null,
                        100.0
                );
            }

            if (contem(texto, "motoboy")) {
                if (estabelecimento().hasCidadesAtendidas()) {
                    return new InterpretacaoIaResponseDTO(
                            TipoSolicitacaoEnum.ENTREGA,
                            CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                            "Nosso servico de Motoboy atende apenas em: " + cidadesAtendidasTexto() + ". Para outras regioes, oferecemos entrega via Correio ou Transportadora para todo o Brasil.",
                            false,
                            null,
                            100.0
                    );
                }
            }

            if (estabelecimento().hasCidadesAtendidas()) {
                return responderEntregaPorCidadesAtendidas(texto, enderecoEnriquecido);
            }

            if (estabelecimento().hasEntregaCustomizada()) {
                return new InterpretacaoIaResponseDTO(
                        TipoSolicitacaoEnum.ENTREGA,
                        CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                        estabelecimento().entrega().trim(),
                        false,
                        null,
                        100.0
                );
            }

            return new InterpretacaoIaResponseDTO(
                    TipoSolicitacaoEnum.ENTREGA,
                    CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                    messagesProperties.entregaGenerica(),
                    false,
                    null,
                    90.0
            );
        }

        return null;
    }

    private boolean historicoIndicaCompra(List<MensagemConversa> historico, String mensagemAtual) {
        if (historico == null) {
            return false;
        }
        return historico.stream()
                .filter(item -> item.direcao() == DirecaoMensagemEnum.CLIENTE)
                .map(MensagemConversa::conteudo)
                .filter(conteudo -> conteudo != null && !conteudo.equals(mensagemAtual))
                .map(this::normalizar)
                .anyMatch(texto -> contem(texto, "comprar", "compra do produto", "pedido do produto", "quero o produto"));
    }

    private InterpretacaoIaResponseDTO resumirPedidoCompleto(
            InterpretacaoIaResponseDTO resposta,
            List<MensagemConversa> historico
    ) {
        if (historico == null || historico.isEmpty()) {
            return null;
        }
        String conversa = historico.stream()
                .filter(item -> item.direcao() == DirecaoMensagemEnum.CLIENTE)
                .map(MensagemConversa::conteudo)
                .filter(conteudo -> conteudo != null && !conteudo.isBlank())
                .reduce((primeiro, seguinte) -> primeiro + "\n" + seguinte)
                .orElse("");
        String produto = extrairProdutoPedido(conversa);
        String quantidade = extrairQuantidade(conversa);
        String pagamento = extrairPagamento(conversa);
        String entrega = extrairEntregaPedido(conversa);
        if (produto == null || quantidade == null || pagamento == null || entrega == null) {
            return null;
        }
        String resumo = "Resumo do pedido: " + produto + ", " + quantidade + " unidade(s), pagamento por "
                + pagamento + ", " + entrega + ". A equipe ira processar o pedido.";
        return new InterpretacaoIaResponseDTO(
                TipoSolicitacaoEnum.COMPRA_PRODUTO,
                CategoriaAtendimentoEnum.ATENDIMENTO_COMERCIAL,
                resumo,
                true,
                "Pedido completo aguarda processamento pela equipe.",
                resposta.confianca()
        ).normalizado();
    }

    private String extrairProdutoPedido(String conversa) {
        Matcher matcher = Pattern.compile("(?i)\\bcomprar\\s+([^\\r\\n.!?]+)").matcher(conversa);
        if (!matcher.find()) {
            return null;
        }
        String produto = matcher.group(1).trim();
        return produto.isBlank() ? null : produto;
    }

    private String extrairQuantidade(String conversa) {
        Matcher matcher = Pattern.compile("(?i)\\b(\\d{1,4})\\s*(?:unidade|unidades|un\\b|itens?\\b)").matcher(conversa);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extrairPagamento(String conversa) {
        String texto = normalizar(conversa);
        if (texto.contains("pix")) {
            return "PIX";
        }
        if (texto.contains("cartao")) {
            return "cartao";
        }
        if (texto.contains("dinheiro")) {
            return "dinheiro";
        }
        if (texto.contains("boleto")) {
            return "boleto";
        }
        return null;
    }

    private String extrairEntregaPedido(String conversa) {
        String texto = normalizar(conversa);
        if (contem(texto, "retirar", "retirada")) {
            return "retirada na loja";
        }
        if (contem(texto, "correio", "transportadora", "motoboy", "entrega")) {
            Matcher cep = Pattern.compile("\\b\\d{5}-?\\d{3}\\b").matcher(texto);
            if (cep.find()) {
                return "entrega no endereco informado, CEP " + cep.group();
            }
        }
        return null;
    }

    private InterpretacaoIaResponseDTO responderEntregaPorCidadesAtendidas(
            String texto,
            EnderecoEnriquecidoDTO enderecoEnriquecido
    ) {
        if (enderecoEnriquecido != null && enderecoEnriquecido.temCidade()) {
            if (cidadeAtendida(enderecoEnriquecido.cidade())) {
                return new InterpretacaoIaResponseDTO(
                        TipoSolicitacaoEnum.ENTREGA,
                        CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                        messagesProperties.entregaCidadeAtendida(enderecoEnriquecido.cidade().trim()),
                        false,
                        null,
                        100.0
                );
            }

            return new InterpretacaoIaResponseDTO(
                    TipoSolicitacaoEnum.ENTREGA,
                    CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                    messagesProperties.entregaForaArea(cidadesAtendidasTexto()),
                    false,
                    null,
                    100.0
            );
        }

        String cep = extrairCep(texto);
        if (cep != null) {
            Optional<CepLookupResponseDTO> cepConsultado = cepLookupClient.consultar(cep);
            if (cepConsultado.isEmpty() || cepConsultado.get().localidade() == null || cepConsultado.get().localidade().isBlank()) {
                return new InterpretacaoIaResponseDTO(
                        TipoSolicitacaoEnum.ENTREGA,
                        CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                        messagesProperties.entregaCepNaoValidado(),
                        false,
                        null,
                        70.0
                );
            }

            String cidadeDoCep = cepConsultado.get().localidade().trim();
            if (cidadeAtendida(cidadeDoCep)) {
                return new InterpretacaoIaResponseDTO(
                        TipoSolicitacaoEnum.ENTREGA,
                        CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                        messagesProperties.entregaCidadeAtendida(cidadeDoCep),
                        false,
                        null,
                        100.0
                );
            }

            return new InterpretacaoIaResponseDTO(
                    TipoSolicitacaoEnum.ENTREGA,
                    CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                    messagesProperties.entregaForaArea(cidadesAtendidasTexto()),
                    false,
                    null,
                    100.0
            );
        }

        String cidadeAtendida = cidadeAtendidaMencionada(texto);
        if (cidadeAtendida != null) {
            return new InterpretacaoIaResponseDTO(
                    TipoSolicitacaoEnum.ENTREGA,
                    CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                    messagesProperties.entregaCidadeAtendida(cidadeAtendida),
                    false,
                    null,
                    100.0
            );
        }

        if (parecePerguntarCidadeEspecifica(texto)) {
            return new InterpretacaoIaResponseDTO(
                    TipoSolicitacaoEnum.ENTREGA,
                    CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                    messagesProperties.entregaForaArea(cidadesAtendidasTexto()),
                    false,
                    null,
                    100.0
            );
        }

        return new InterpretacaoIaResponseDTO(
                TipoSolicitacaoEnum.ENTREGA,
                CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                "Realizamos entregas por Correio e Transportadora para todo o Brasil. O Motoboy atende somente "
                        + "nas cidades: " + cidadesAtendidasTexto() + ". Tambem e possivel retirar na loja.",
                false,
                null,
                100.0
        );
    }

    private String cidadeAtendidaMencionada(String texto) {
        for (String cidade : cidadesAtendidas()) {
            if (texto.contains(normalizar(cidade))) {
                return cidade.trim();
            }
        }
        return null;
    }

    private String extrairCep(String texto) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\b\\d{5}-?\\d{3}\\b")
                .matcher(texto);

        if (!matcher.find()) {
            return null;
        }

        return somenteDigitos(matcher.group());
    }

    private String somenteDigitos(String valor) {
        return valor.replaceAll("\\D", "");
    }

    private String cidadesAtendidasTexto() {
        return String.join(", ", cidadesAtendidas());
    }

    private List<String> cidadesAtendidas() {
        return estabelecimento().cidadesAtendidas().stream()
                .filter(cidade -> cidade != null && !cidade.isBlank())
                .map(String::trim)
                .toList();
    }

    private boolean cidadeAtendida(String cidadeInformada) {
        String cidadeNormalizada = normalizar(cidadeInformada);
        return cidadesAtendidas().stream()
                .map(this::normalizar)
                .anyMatch(cidadeNormalizada::equals);
    }

    private EstabelecimentoProperties estabelecimento() {
        return estabelecimentoComercialProvider.obter();
    }

    private boolean parecePerguntarCidadeEspecifica(String texto) {
        return texto.matches(".*\\b(em|para|pra|na|no)\\s+[a-z].*")
                && !texto.matches(".*\\b(rua|avenida|av|alameda|travessa|praca|rodovia|estrada)\\b.*");
    }

    private String motivoPadrao(TipoSolicitacaoEnum tipoSolicitacao) {
        if (tipoSolicitacao == TipoSolicitacaoEnum.ORCAMENTO_FORMULA) {
            return "Necessario analise da receita ou formula pela farmacia.";
        }
        if (tipoSolicitacao == TipoSolicitacaoEnum.DUVIDA_FARMACEUTICA) {
            return "Mensagem envolve orientacao farmaceutica ou clinica.";
        }
        if (tipoSolicitacao == TipoSolicitacaoEnum.COMPRA_PRODUTO) {
            return "Pedido de produto aguarda processamento pela equipe.";
        }
        return "Solicitacao precisa de avaliacao da equipe responsavel.";
    }

    private String referenciaCliente(Atendimento atendimento) {
        String chave = atendimento.clienteChave();
        if (chave == null || chave.isBlank()) {
            chave = ClienteChave.criar(atendimento.telefoneCliente());
        }
        int separador = chave.indexOf(':');
        String hash = separador >= 0 ? chave.substring(separador + 1) : chave;
        return hash.substring(0, Math.min(12, hash.length()));
    }

    private InterpretacaoIaResponseDTO aplicarTerminologiaCliente(InterpretacaoIaResponseDTO resposta) {
        return new InterpretacaoIaResponseDTO(
                resposta.tipoSolicitacao(),
                resposta.categoria(),
                aplicarTerminologiaCliente(resposta.respostaGerada()),
                resposta.necessitaAtendimentoHumano(),
                aplicarTerminologiaCliente(resposta.motivoEncaminhamento()),
                resposta.confianca()
        );
    }

    private ChatbotResponseDTO aplicarTerminologiaCliente(ChatbotResponseDTO resposta) {
        if (resposta == null) {
            return null;
        }
        return new ChatbotResponseDTO(
                resposta.idAtendimento(),
                resposta.origem(),
                resposta.mensagemCliente(),
                resposta.tipoSolicitacao(),
                resposta.categoria(),
                aplicarTerminologiaCliente(resposta.respostaGerada()),
                resposta.necessitaAtendimentoHumano(),
                aplicarTerminologiaCliente(resposta.motivoEncaminhamento()),
                resposta.confianca(),
                resposta.status(),
                resposta.dataProcessamento()
        );
    }

    private String aplicarTerminologiaCliente(String texto) {
        if (texto == null) {
            return null;
        }
        return texto
                .replaceAll("(?iu)\\batendimento\\s+human[oa]\\b", "atendimento da equipe")
                .replaceAll("(?iu)\\ban[aá]lise\\s+human[oa]\\b", "analise da equipe")
                .replaceAll("(?iu)\\bavalia[cç][aã]o\\s+human[oa]\\b", "avaliacao da equipe")
                .replaceAll("(?iu)\\bencaminhamento\\s+human[oa]\\b", "encaminhamento para a equipe")
                .replaceAll("(?iu)\\bpara\\s+(?:um|uma|o|a)?\\s*human[oa]\\b", "para a equipe")
                .replaceAll("(?iu)\\bhuman[oa]\\b", "equipe");
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
