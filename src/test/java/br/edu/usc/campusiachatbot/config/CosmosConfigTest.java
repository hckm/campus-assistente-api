package br.edu.usc.campusiachatbot.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class CosmosConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CosmosConfig.class, PropertiesScanConfiguration.class);

    @ParameterizedTest
    @ValueSource(strings = {"local", "test", "prod"})
    void devePermanecerDesabilitadoSemCredenciaisNosPerfisAtuais(String perfil) {
        try (var builders = mockConstruction(CosmosClientBuilder.class);
             var credenciais = mockConstruction(ManagedIdentityCredentialBuilder.class)) {
            runner.withPropertyValues("spring.profiles.active=" + perfil).run(context -> {
                assertThat(context).hasNotFailed().doesNotHaveBean(CosmosClient.class)
                        .doesNotHaveBean(CosmosProperties.class);
                assertThat(builders.constructed()).isEmpty();
                assertThat(credenciais.constructed()).isEmpty();
            });
        }
    }

    @Test
    void deveIgnorarPropriedadesCosmosInvalidasQuandoDesabilitadoExplicitamente() {
        runner.withPropertyValues("azure.cosmos.enabled=false", "azure.cosmos.endpoint=endpoint invalido",
                "azure.cosmos.autenticacao=invalida").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(CosmosProperties.class)
                    .doesNotHaveBean(CosmosClient.class);
        });
    }

    @Test
    void deveExigirEndpointSomenteQuandoHabilitado() {
        try (var builders = mockConstruction(CosmosClientBuilder.class)) {
            runner.withPropertyValues("azure.cosmos.enabled=true").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("endpoint");
                assertThat(builders.constructed()).isEmpty();
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "00000000-0000-0000-0000-000000000001"})
    void deveConfigurarIdentidadeESingletonComFechamentoSemExecutarSdkReal(String clientId) {
        CosmosClient client = mock(CosmosClient.class);
        ManagedIdentityCredential credential = mock(ManagedIdentityCredential.class);
        try (var builders = mockBuilders(client);
             var credenciais = mockConstruction(ManagedIdentityCredentialBuilder.class,
                     withSettings().defaultAnswer(RETURNS_SELF),
                     (builder, context) -> when(builder.build()).thenReturn(credential))) {
            habilitado().withPropertyValues("spring.profiles.active=prod",
                    "azure.cosmos.database=database-teste", "azure.cosmos.conversas-container=conversas-teste",
                    "azure.cosmos.catalogo-container=catalogo-teste",
                    "azure.cosmos.estabelecimentos-container=estabelecimentos-teste",
                    "azure.cosmos.catalogo-id=renovo-teste",
                    "azure.cosmos.estabelecimento-id=renovo-teste",
                    "azure.cosmos.catalogo-batch-max-operations=90",
                    "azure.cosmos.catalogo-batch-max-bytes=1800000",
                    "azure.cosmos.managed-identity-client-id=" + clientId).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(CosmosClient.class);
                assertThat(context.getBean(CosmosClient.class)).isSameAs(client);
                assertThat(context.getBean(CosmosClient.class)).isSameAs(context.getBean(CosmosClient.class));
                CosmosProperties properties = context.getBean(CosmosProperties.class);
                assertThat(properties.getDatabase()).isEqualTo("database-teste");
                assertThat(properties.getConversasContainer()).isEqualTo("conversas-teste");
                assertThat(properties.getCatalogoContainer()).isEqualTo("catalogo-teste");
                assertThat(properties.getEstabelecimentosContainer()).isEqualTo("estabelecimentos-teste");
                assertThat(properties.getCatalogoId()).isEqualTo("renovo-teste");
                assertThat(properties.getEstabelecimentoId()).isEqualTo("renovo-teste");
                assertThat(properties.getCatalogoBatchMaxOperations()).isEqualTo(90);
                assertThat(properties.getCatalogoBatchMaxBytes()).isEqualTo(1800000);
                assertThat(builders.constructed()).hasSize(1);
                CosmosClientBuilder builder = builders.constructed().getFirst();
                verify(builder).endpoint("https://conta-teste.documents.azure.com");
                verify(builder).credential(credential);
                verify(builder).directMode();
                verify(builder).consistencyLevel(ConsistencyLevel.SESSION);
                verify(builder).buildClient();
                verify(builder, never()).key(any());
                assertThat(credenciais.constructed()).hasSize(1);
                if (clientId.isEmpty()) {
                    verify(credenciais.constructed().getFirst(), never()).clientId(any());
                } else {
                    verify(credenciais.constructed().getFirst()).clientId(clientId);
                }
                verifyNoInteractions(client, credential);
            });
            verify(client).close();
            verifyNoMoreInteractions(client);
            verifyNoInteractions(credential);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "127.0.0.1", "[::1]"})
    void devePermitirEmuladorSomenteLocalComChaveExternaEGateway(String host) {
        CosmosClient client = mock(CosmosClient.class);
        try (var builders = mockBuilders(client);
             var credenciais = mockConstruction(ManagedIdentityCredentialBuilder.class)) {
            habilitado().withPropertyValues("spring.profiles.active=local", "azure.cosmos.autenticacao=EMULATOR",
                    "azure.cosmos.endpoint=https://" + host + ":8081",
                    "azure.cosmos.emulator-key=chave-sintetica").run(context -> {
                assertThat(context).hasNotFailed();
                CosmosProperties properties = context.getBean(CosmosProperties.class);
                assertThat(properties.getDatabase()).isEqualTo("campus-ia-chatbot");
                assertThat(properties.toString()).doesNotContain("chave-sintetica");
                CosmosClientBuilder builder = builders.constructed().getFirst();
                verify(builder).key("chave-sintetica");
                verify(builder).gatewayMode();
                verify(builder).endpointDiscoveryEnabled(false);
                verify(builder).consistencyLevel(ConsistencyLevel.SESSION);
                assertThat(credenciais.constructed()).isEmpty();
                verifyNoInteractions(client);
            });
            verify(client).close();
            verifyNoMoreInteractions(client);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "local,prod", "test", "default"})
    void deveRejeitarEmuladorForaDoPerfilLocalExclusivoDeProducao(String perfis) {
        try (var builders = mockConstruction(CosmosClientBuilder.class)) {
            habilitado().withPropertyValues("spring.profiles.active=" + perfis,
                    "azure.cosmos.autenticacao=EMULATOR", "azure.cosmos.endpoint=https://localhost:8081",
                    "azure.cosmos.emulator-key=chave-sintetica").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseMessage(
                        "Autenticacao EMULATOR exige perfil local e nao permite perfil prod");
                assertThat(builders.constructed()).isEmpty();
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"azure.cosmos.endpoint=http://localhost:8081", "azure.cosmos.endpoint=",
            "azure.cosmos.endpoint=https://host:70000", "azure.cosmos.endpoint=https://host:0",
            "azure.cosmos.endpoint=https://usuario@host", "azure.cosmos.endpoint=https://host/path",
            "azure.cosmos.endpoint=https://host?query=value", "azure.cosmos.endpoint=https://host#fragmento",
            "azure.cosmos.database=", "azure.cosmos.database=nome/invalido",
            "azure.cosmos.conversas-container=", "azure.cosmos.catalogo-container=nome?invalido",
            "azure.cosmos.estabelecimentos-container=nome?invalido", "azure.cosmos.catalogo-container=conversas",
            "azure.cosmos.estabelecimentos-container=conversas", "azure.cosmos.estabelecimento-id=",
            "azure.cosmos.estabelecimento-id=nome/invalido", "azure.cosmos.catalogo-id=",
            "azure.cosmos.catalogo-id=nome/invalido", "azure.cosmos.catalogo-batch-max-operations=0",
            "azure.cosmos.catalogo-batch-max-operations=101", "azure.cosmos.catalogo-batch-max-bytes=0",
            "azure.cosmos.catalogo-batch-max-bytes=1900001", "azure.cosmos.autenticacao=INVALIDA"})
    void deveValidarPropriedadesAntesDeConstruirCliente(String propriedadeInvalida) {
        try (var builders = mockConstruction(CosmosClientBuilder.class)) {
            habilitado().withPropertyValues(propriedadeInvalida).run(context -> {
                assertThat(context).hasFailed();
                assertThat(builders.constructed()).isEmpty();
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"azure.cosmos.endpoint=https://conta-teste.documents.azure.com",
            "azure.cosmos.emulator-key=", "azure.cosmos.managed-identity-client-id=id-sintetico"})
    void deveRejeitarConfiguracaoInseguraOuIncompletaDoEmulador(String propriedadeInvalida) {
        try (var builders = mockConstruction(CosmosClientBuilder.class)) {
            habilitado().withPropertyValues("spring.profiles.active=local", "azure.cosmos.autenticacao=EMULATOR",
                    "azure.cosmos.endpoint=https://localhost:8081", "azure.cosmos.emulator-key=chave-sintetica")
                    .withPropertyValues(propriedadeInvalida).run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).hasStackTraceContaining("Autenticacao EMULATOR")
                                .hasStackTraceContaining("IllegalStateException");
                        assertThat(builders.constructed()).isEmpty();
                    });
        }
    }

    @Test
    void deveRejeitarChaveDeEmuladorNoModoIdentidadeSemExporSeuValor() {
        habilitado().withPropertyValues("azure.cosmos.emulator-key=SEGREDO_SINTETICO").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "emulator-key somente pode ser usada com autenticacao EMULATOR");
            assertThat(context.getStartupFailure()).hasStackTraceContaining("emulator-key");
            assertThat(context.getStartupFailure().toString()).doesNotContain("SEGREDO_SINTETICO");
        });
    }

    private ApplicationContextRunner habilitado() {
        return runner.withPropertyValues("azure.cosmos.enabled=true",
                "azure.cosmos.endpoint=https://conta-teste.documents.azure.com");
    }

    private MockedConstruction<CosmosClientBuilder> mockBuilders(CosmosClient client) {
        return mockConstruction(CosmosClientBuilder.class, withSettings().defaultAnswer(RETURNS_SELF),
                (builder, context) -> when(builder.buildClient()).thenReturn(client));
    }

    @Configuration(proxyBeanMethods = false)
    @ConfigurationPropertiesScan(basePackageClasses = CosmosProperties.class)
    static class PropertiesScanConfiguration {
    }
}
