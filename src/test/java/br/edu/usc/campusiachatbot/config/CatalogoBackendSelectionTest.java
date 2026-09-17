package br.edu.usc.campusiachatbot.config;

import br.edu.usc.campusiachatbot.repository.CosmosCatalogoStore;
import br.edu.usc.campusiachatbot.repository.JpaCatalogoStore;
import br.edu.usc.campusiachatbot.repository.CatalogoRenovoRepository;
import br.edu.usc.campusiachatbot.service.CatalogoRenovoDataLoader;
import br.edu.usc.campusiachatbot.store.CatalogoStore;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogoBackendSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SelectionConfiguration.class, PropertiesScanConfiguration.class)
            .withBean(CatalogoRenovoRepository.class, () -> mock(CatalogoRenovoRepository.class))
            .withBean(ResourceLoader.class, () -> mock(ResourceLoader.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void deveSelecionarJpaECarregadorPorPadrao() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(CatalogoStore.class)
                    .hasSingleBean(JpaCatalogoStore.class)
                    .hasSingleBean(CatalogoRenovoDataLoader.class)
                    .doesNotHaveBean(CosmosCatalogoStore.class);
        });
    }

    @Test
    void deveSelecionarSomenteCosmosQuandoExplicitamenteHabilitado() {
        CosmosClient client = mock(CosmosClient.class);
        CosmosDatabase database = mock(CosmosDatabase.class);
        CosmosContainer container = mock(CosmosContainer.class);
        CosmosProperties properties = new CosmosProperties();
        properties.setDatabase("database-teste");
        properties.setCatalogoContainer("catalogo-teste");
        when(client.getDatabase("database-teste")).thenReturn(database);
        when(database.getContainer("catalogo-teste")).thenReturn(container);

        runner.withPropertyValues("catalogo.backend=COSMOS", "azure.cosmos.enabled=true")
                .withBean(CosmosClient.class, () -> client)
                .withBean(CosmosProperties.class, () -> properties)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CatalogoStore.class)
                            .doesNotHaveBean(JpaCatalogoStore.class)
                            .doesNotHaveBean(CatalogoRenovoDataLoader.class);
                    assertThat(context.getBean(CatalogoStore.class)).isInstanceOf(CosmosCatalogoStore.class);
                });
    }

    @Test
    void deveFalharClaramenteQuandoCosmosForSelecionadoSemHabilitacao() {
        runner.withPropertyValues("catalogo.backend=COSMOS").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "catalogo.backend=COSMOS exige azure.cosmos.enabled=true"
            );
        });
    }

    @Test
    void deveRejeitarBackendDesconhecido() {
        runner.withPropertyValues("catalogo.backend=DESCONHECIDO").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("catalogo.backend");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({JpaCatalogoStore.class, CatalogoRenovoDataLoader.class, CosmosCatalogoStoreConfig.class})
    static class SelectionConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @ConfigurationPropertiesScan(basePackageClasses = CatalogoBackendProperties.class)
    static class PropertiesScanConfiguration {
    }
}
