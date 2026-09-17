package br.edu.usc.campusiachatbot.config;

import br.edu.usc.campusiachatbot.CampusIaChatbotApplication;
import br.edu.usc.campusiachatbot.dto.ChatbotRequestDTO;
import br.edu.usc.campusiachatbot.enums.OrigemMensagemEnum;
import br.edu.usc.campusiachatbot.repository.AtendimentoRepository;
import br.edu.usc.campusiachatbot.service.AtendimentoService;
import br.edu.usc.campusiachatbot.service.GeminiService;
import br.edu.usc.campusiachatbot.service.InterpretacaoIaService;
import com.azure.cosmos.CosmosClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;

class CosmosDisabledProfilesTest {

    @ParameterizedTest
    @ValueSource(strings = {"local", "test", "prod"})
    void deveIniciarPerfilSqlComBancoIsoladoECosmosDesabilitado(String perfil) throws Exception {
        String database = "jdbc:h2:mem:cosmos_disabled_" + perfil;
        try (var builders = mockConstruction(com.azure.cosmos.CosmosClientBuilder.class);
             var context = new SpringApplicationBuilder(CampusIaChatbotApplication.class)
                     .web(WebApplicationType.NONE)
                     .profiles(perfil)
                     .run("--azure.cosmos.enabled=false",
                             "--spring.datasource.url=" + database,
                             "--spring.datasource.driver-class-name=org.h2.Driver",
                             "--spring.datasource.username=sa", "--spring.datasource.password=",
                             "--spring.jpa.hibernate.ddl-auto=create-drop",
                             "--gemini.api-key=", "--cep-lookup.enabled=false")) {
            assertThat(context.getEnvironment().getActiveProfiles()).contains(perfil);
            assertThat(context.getBeansOfType(CosmosClient.class)).isEmpty();
            assertThat(context.getBeansOfType(CosmosProperties.class)).isEmpty();
            assertThat(builders.constructed()).isEmpty();
            assertThat(context.getBean(InterpretacaoIaService.class)).isInstanceOf(GeminiService.class);
            try (var connection = context.getBean(DataSource.class).getConnection()) {
                assertThat(connection.getMetaData().getURL()).isEqualTo(database);
            }
            AtendimentoService atendimentos = context.getBean(AtendimentoService.class);
            var atendimento = atendimentos.criarRecebido(
                    new ChatbotRequestDTO("00000000000", "Teste", "Mensagem sintetica", null),
                    OrigemMensagemEnum.SIMULADOR);
            assertThat(atendimento.id()).isNotNull();
            assertThat(atendimentos.buscarHistoricoMensagens(atendimento.id(), 10)).hasSize(1);
            assertThat(context.getBean(AtendimentoRepository.class).count()).isEqualTo(1);
        }
    }
}
