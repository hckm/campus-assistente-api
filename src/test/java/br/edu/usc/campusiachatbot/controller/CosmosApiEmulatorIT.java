package br.edu.usc.campusiachatbot.controller;

import br.edu.usc.campusiachatbot.config.CosmosProperties;
import br.edu.usc.campusiachatbot.domain.Atendimento;
import br.edu.usc.campusiachatbot.domain.InicioInteracao;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.dto.ChatbotResponseDTO;
import br.edu.usc.campusiachatbot.dto.PortalChatRequestDTO;
import br.edu.usc.campusiachatbot.enums.DirecaoMensagemEnum;
import br.edu.usc.campusiachatbot.enums.OrigemMensagemEnum;
import br.edu.usc.campusiachatbot.enums.StatusAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.CategoriaAtendimentoEnum;
import br.edu.usc.campusiachatbot.enums.TipoSolicitacaoEnum;
import br.edu.usc.campusiachatbot.exception.IdempotenciaConflitoException;
import br.edu.usc.campusiachatbot.exception.InteracaoEmAndamentoException;
import br.edu.usc.campusiachatbot.exception.InteracaoFalhouException;
import br.edu.usc.campusiachatbot.repository.ClienteChave;
import br.edu.usc.campusiachatbot.service.AtendimentoService;
import br.edu.usc.campusiachatbot.store.AtendimentoStore;
import br.edu.usc.campusiachatbot.store.CatalogoStore;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "gemini.api-key=",
                "cep-lookup.enabled=false",
                "estabelecimento.nome=Farmacia Cosmos",
                "estabelecimento.horario-funcionamento=Segunda a sexta das 08:00 as 18:00",
                "logging.level.com.azure.cosmos=WARN"
        }
)
@ActiveProfiles({"local", "cosmos"})
@EnabledIfEnvironmentVariable(named = "COSMOS_EMULATOR_TEST", matches = "true")
class CosmosApiEmulatorIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AtendimentoStore atendimentoStore;

    @Autowired
    private AtendimentoService atendimentoService;

    @Autowired
    private CatalogoStore catalogoStore;

    @Autowired
    private CosmosClient cosmosClient;

    @Autowired
    private CosmosProperties cosmosProperties;

    private final Set<String> clientesCriados = new LinkedHashSet<>();

    @AfterEach
    void limparConversasDaProva() {
        CosmosContainer container = cosmosClient.getDatabase(cosmosProperties.getDatabase())
                .getContainer(cosmosProperties.getConversasContainer());
        clientesCriados.forEach(clienteChave -> excluirParticao(container, clienteChave));
        clientesCriados.clear();
    }

    @Test
    void deveExecutarFluxoCompletoComCosmos() {
        String telefone = telefoneUnico();
        registrar(telefone);
        ChatbotRequestDTO primeiraMensagem = request(telefone, "Qual o horario de funcionamento?");

        ResponseEntity<ChatbotResponseDTO> primeira = enviar(primeiraMensagem, "turno-1");
        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(primeira.getBody()).isNotNull();
        assertThat(primeira.getBody().status()).isEqualTo(StatusAtendimentoEnum.PROCESSADO);
        assertThat(primeira.getBody().idAtendimento()).isNotBlank();

        ResponseEntity<ChatbotResponseDTO> repetida = enviar(
                request(telefone, "Qual o horario de funcionamento?"),
                "turno-1"
        );
        assertThat(repetida.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(repetida.getBody()).isEqualTo(primeira.getBody());

        ResponseEntity<JsonNode> conflito = enviarErro(
                request(telefone, "Qual o endereco?"),
                "turno-1"
        );
        assertThat(conflito.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<ChatbotResponseDTO> continuacao = enviar(
                request(telefone, "Quais formas de pagamento voces aceitam?"),
                "turno-2"
        );
        assertThat(continuacao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(continuacao.getBody()).isNotNull();
        assertThat(continuacao.getBody().idAtendimento()).isEqualTo(primeira.getBody().idAtendimento());

        Atendimento atendimento = atendimentoStore.buscarPorId(primeira.getBody().idAtendimento()).orElseThrow();
        assertThat(atendimentoStore.buscarHistoricoMensagens(atendimento, 10))
                .extracting(mensagem -> mensagem.direcao())
                .containsExactly(
                        DirecaoMensagemEnum.CLIENTE,
                        DirecaoMensagemEnum.BOT,
                        DirecaoMensagemEnum.CLIENTE,
                        DirecaoMensagemEnum.BOT
                );

        ResponseEntity<ChatbotResponseDTO> consulta = restTemplate.getForEntity(
                "/api/atendimentos/" + primeira.getBody().idAtendimento(),
                ChatbotResponseDTO.class
        );
        assertThat(consulta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(consulta.getBody()).isEqualTo(continuacao.getBody());

        String telefoneHumano = telefoneUnico();
        registrar(telefoneHumano);
        ResponseEntity<ChatbotResponseDTO> humano = enviar(
                request(telefoneHumano, "Qual dose desse medicamento posso dar para uma crianca?"),
                "humano-1"
        );
        assertThat(humano.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(humano.getBody()).isNotNull();
        assertThat(humano.getBody().necessitaAtendimentoHumano()).isTrue();
        assertThat(humano.getBody().status()).isEqualTo(StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA);

        ResponseEntity<ChatbotResponseDTO> continuacaoHumano = enviar(
                request(telefoneHumano, "Qual o horario de funcionamento?"),
                "humano-2"
        );
        assertThat(continuacaoHumano.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(continuacaoHumano.getBody()).isNotNull();
        assertThat(continuacaoHumano.getBody().idAtendimento()).isEqualTo(humano.getBody().idAtendimento());
        assertThat(continuacaoHumano.getBody().status()).isEqualTo(StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA);
        assertThat(continuacaoHumano.getBody().necessitaAtendimentoHumano()).isTrue();
        assertThat(continuacaoHumano.getBody().motivoEncaminhamento())
                .isEqualTo(humano.getBody().motivoEncaminhamento());
        Atendimento atendimentoHumano = atendimentoStore.buscarPorId(humano.getBody().idAtendimento()).orElseThrow();
        assertThat(atendimentoStore.buscarHistoricoMensagens(atendimentoHumano, 10))
                .extracting(mensagem -> mensagem.direcao())
                .containsExactly(
                        DirecaoMensagemEnum.CLIENTE,
                        DirecaoMensagemEnum.BOT,
                        DirecaoMensagemEnum.CLIENTE,
                        DirecaoMensagemEnum.BOT
                );

        ResponseEntity<JsonNode> paginaUm = restTemplate.getForEntity(
                "/api/atendimentos?tamanho=1",
                JsonNode.class
        );
        assertThat(paginaUm.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(paginaUm.getBody()).isNotNull();
        assertThat(paginaUm.getBody().path("itens").size()).isEqualTo(1);
        String token = paginaUm.getBody().path("proximoToken").asText();
        assertThat(token).isNotBlank();

        String paginaDoisUri = UriComponentsBuilder.fromPath("/api/atendimentos")
                .queryParam("tamanho", 1)
                .queryParam("token", token)
                .toUriString();
        ResponseEntity<JsonNode> paginaDois = restTemplate.getForEntity(paginaDoisUri, JsonNode.class);
        assertThat(paginaDois.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(paginaDois.getBody()).isNotNull();
        assertThat(paginaDois.getBody().path("itens").size()).isEqualTo(1);
        assertThat(paginaDois.getBody().path("itens").get(0).path("idAtendimento").asText())
                .isNotEqualTo(paginaUm.getBody().path("itens").get(0).path("idAtendimento").asText());

        ResponseEntity<JsonNode> humanos = restTemplate.getForEntity(
                "/api/atendimentos/status/AGUARDANDO_ANALISE_HUMANA?tamanho=100",
                JsonNode.class
        );
        assertThat(humanos.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(humanos.getBody()).isNotNull();
        assertThat(humanos.getBody().path("itens").findValuesAsText("idAtendimento"))
                .contains(humano.getBody().idAtendimento());

        assertThat(catalogoStore.listarTodosOrdenadosPorCodigo()).hasSize(19);
    }

    @Test
    void deveBloquearInteracoesConcorrentesDoMesmoCliente() {
        String telefone = telefoneUnico();
        registrar(telefone);
        ChatbotRequestDTO request = request(telefone, "Qual o horario?");

        atendimentoService.iniciarInteracao(request, OrigemMensagemEnum.SIMULADOR, null);

        assertThatThrownBy(() ->
                atendimentoService.iniciarInteracao(request, OrigemMensagemEnum.SIMULADOR, null))
                .isInstanceOf(InteracaoEmAndamentoException.class);
    }

    @Test
    void deveEncerrarEventoExpiradoSemAceitarRespostaTardia() {
        String telefone = telefoneUnico();
        registrar(telefone);
        LocalDateTime inicio = LocalDateTime.of(2026, 9, 7, 12, 0);
        Atendimento recebido = recebido(telefone, inicio);
        InicioInteracao primeira = atendimentoStore.iniciarInteracao(
                recebido,
                List.of(StatusAtendimentoEnum.RECEBIDO, StatusAtendimentoEnum.PROCESSADO),
                inicio.minusMinutes(30),
                inicio.plusSeconds(1),
                "evento-expirado",
                "payload-a"
        );
        Atendimento repetidoDepoisDaExpiracao = recebido(telefone, inicio.plusSeconds(2));

        assertThatThrownBy(() -> atendimentoStore.iniciarInteracao(
                repetidoDepoisDaExpiracao,
                List.of(StatusAtendimentoEnum.RECEBIDO, StatusAtendimentoEnum.PROCESSADO),
                inicio.minusMinutes(30),
                inicio.plusSeconds(3),
                "evento-expirado",
                "payload-a"
        )).isInstanceOf(InteracaoFalhouException.class);

        Atendimento respostaTardia = new Atendimento(
                primeira.atendimento().id(),
                primeira.atendimento().clienteChave(),
                telefone,
                "Cliente Cosmos",
                OrigemMensagemEnum.SIMULADOR,
                "Qual o horario?",
                TipoSolicitacaoEnum.HORARIO_FUNCIONAMENTO,
                CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                "Resposta tardia",
                false,
                null,
                100.0,
                StatusAtendimentoEnum.PROCESSADO,
                inicio.plusSeconds(3)
        );
        assertThatThrownBy(() -> atendimentoStore.registrarRespostaComMensagemBot(primeira, respostaTardia))
                .isInstanceOf(InteracaoEmAndamentoException.class);

        assertThatThrownBy(() -> atendimentoStore.iniciarInteracao(
                repetidoDepoisDaExpiracao,
                List.of(StatusAtendimentoEnum.RECEBIDO, StatusAtendimentoEnum.PROCESSADO),
                inicio.minusMinutes(30),
                inicio.plusSeconds(4),
                "evento-expirado",
                "payload-b"
        )).isInstanceOf(IdempotenciaConflitoException.class);

        InicioInteracao nova = atendimentoStore.iniciarInteracao(
                repetidoDepoisDaExpiracao,
                List.of(StatusAtendimentoEnum.RECEBIDO, StatusAtendimentoEnum.PROCESSADO),
                inicio.minusMinutes(30),
                inicio.plusSeconds(4),
                "evento-novo",
                "payload-a"
        );
        assertThat(nova.atendimento().id()).isNotEqualTo(primeira.atendimento().id());
    }

    @Test
    void deveCriarNovaConversaAposFinalizacaoErroInatividadeEOuClienteDiferente() {
        String telefone = telefoneUnico();
        registrar(telefone);
        LocalDateTime inicio = LocalDateTime.of(2026, 9, 7, 14, 0);

        InicioInteracao finalizavel = atendimentoStore.iniciarInteracao(
                recebido(telefone, inicio), statusAtivos(), inicio.minusMinutes(30), inicio.plusMinutes(1),
                "finalizacao", "payload-finalizacao"
        );
        atendimentoStore.registrarRespostaComMensagemBot(
                finalizavel, respondido(finalizavel.atendimento(), StatusAtendimentoEnum.FINALIZADO, inicio.plusSeconds(1))
        );
        InicioInteracao aposFinalizacao = atendimentoStore.iniciarInteracao(
                recebido(telefone, inicio.plusSeconds(2)), statusAtivos(), inicio.minusMinutes(30), inicio.plusMinutes(1),
                "apos-finalizacao", "payload-apos-finalizacao"
        );
        assertThat(aposFinalizacao.atendimento().id()).isNotEqualTo(finalizavel.atendimento().id());

        atendimentoStore.marcarErro(aposFinalizacao, inicio.plusSeconds(3));
        InicioInteracao aposErro = atendimentoStore.iniciarInteracao(
                recebido(telefone, inicio.plusSeconds(4)), statusAtivos(), inicio.minusMinutes(30), inicio.plusMinutes(2),
                "apos-erro", "payload-apos-erro"
        );
        assertThat(aposErro.atendimento().id()).isNotEqualTo(aposFinalizacao.atendimento().id());
        atendimentoStore.registrarRespostaComMensagemBot(
                aposErro, respondido(aposErro.atendimento(), StatusAtendimentoEnum.PROCESSADO, inicio.plusSeconds(5))
        );

        InicioInteracao aposInatividade = atendimentoStore.iniciarInteracao(
                recebido(telefone, inicio.plusMinutes(36)), statusAtivos(), inicio.plusMinutes(6), inicio.plusMinutes(37),
                "apos-inatividade", "payload-apos-inatividade"
        );
        assertThat(aposInatividade.atendimento().id()).isNotEqualTo(aposErro.atendimento().id());

        String outroTelefone = telefoneUnico();
        registrar(outroTelefone);
        InicioInteracao outraSessao = atendimentoStore.iniciarInteracao(
                recebido(outroTelefone, inicio), statusAtivos(), inicio.minusMinutes(30), inicio.plusMinutes(1),
                "outra-sessao", "payload-outra-sessao"
        );
        assertThat(outraSessao.atendimento().id()).isNotEqualTo(aposInatividade.atendimento().id());
    }

    private ResponseEntity<ChatbotResponseDTO> enviar(ChatbotRequestDTO request, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return restTemplate.exchange(
                "/api/portal/chat",
                HttpMethod.POST,
                new HttpEntity<>(new PortalChatRequestDTO(request.telefoneCliente(), request.mensagem()), headers),
                ChatbotResponseDTO.class
        );
    }

    private ResponseEntity<JsonNode> enviarErro(ChatbotRequestDTO request, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return restTemplate.exchange(
                "/api/portal/chat",
                HttpMethod.POST,
                new HttpEntity<>(new PortalChatRequestDTO(request.telefoneCliente(), request.mensagem()), headers),
                JsonNode.class
        );
    }

    private ChatbotRequestDTO request(String telefone, String mensagem) {
        return new ChatbotRequestDTO(telefone, "Cliente Cosmos", mensagem, OrigemMensagemEnum.SIMULADOR);
    }

    private String telefoneUnico() {
        long numero = UUID.randomUUID().getMostSignificantBits() & 0x7fffffffL;
        return "149" + String.format("%08d", numero % 100_000_000L);
    }

    private Atendimento recebido(String telefone, LocalDateTime data) {
        return new Atendimento(
                null,
                ClienteChave.criar(telefone),
                telefone,
                "Cliente Cosmos",
                OrigemMensagemEnum.SIMULADOR,
                "Qual o horario?",
                null,
                null,
                null,
                false,
                null,
                null,
                StatusAtendimentoEnum.RECEBIDO,
                data
        );
    }

    private List<StatusAtendimentoEnum> statusAtivos() {
        return List.of(
                StatusAtendimentoEnum.RECEBIDO,
                StatusAtendimentoEnum.PROCESSADO,
                StatusAtendimentoEnum.AGUARDANDO_ANALISE_HUMANA
        );
    }

    private Atendimento respondido(Atendimento atendimento, StatusAtendimentoEnum status, LocalDateTime data) {
        return new Atendimento(
                atendimento.id(),
                atendimento.clienteChave(),
                atendimento.telefoneCliente(),
                atendimento.nomeCliente(),
                atendimento.origem(),
                atendimento.mensagemCliente(),
                TipoSolicitacaoEnum.HORARIO_FUNCIONAMENTO,
                CategoriaAtendimentoEnum.ATENDIMENTO_ADMINISTRATIVO,
                "Resposta",
                false,
                null,
                100.0,
                status,
                data
        );
    }

    private void registrar(String telefone) {
        clientesCriados.add(ClienteChave.criar(telefone));
    }

    private void excluirParticao(CosmosContainer container, String clienteChave) {
        PartitionKey partitionKey = new PartitionKey(clienteChave);
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT VALUE c.id FROM c WHERE c.clienteChave = @clienteChave",
                List.of(new SqlParameter("@clienteChave", clienteChave))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions().setPartitionKey(partitionKey);
        List<String> ids = new ArrayList<>();
        container.queryItems(query, options, String.class).iterableByPage()
                .forEach(page -> ids.addAll(page.getResults()));
        ids.forEach(id -> container.deleteItem(id, partitionKey, new CosmosItemRequestOptions()));
    }
}
