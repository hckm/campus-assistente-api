package br.edu.usc.campusiachatbot.config;

import br.edu.usc.campusiachatbot.repository.CosmosCatalogoStore;
import br.edu.usc.campusiachatbot.service.CosmosSeedDataLoader;
import br.edu.usc.campusiachatbot.store.EstabelecimentoComercialStore;
import com.azure.cosmos.CosmosClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CosmosRunnerActivationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RunnersConfiguration.class);

    @Test
    void naoRegistraRunnersEmProdCosmosSemOptIn() {
        runner.withPropertyValues(
                "spring.profiles.active=prod,cosmos",
                "azure.cosmos.provisioning.enabled=false",
                "azure.cosmos.seed.enabled=false"
        ).run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(CosmosResourceInitializer.class)
                .doesNotHaveBean(CosmosSeedDataLoader.class));
    }

    @Test
    void registraRunnersQuandoEmuladorHabilitaFlags() {
        runner.withPropertyValues(
                "spring.profiles.active=local,cosmos",
                "azure.cosmos.provisioning.enabled=true",
                "azure.cosmos.seed.enabled=true"
        ).withBean(CosmosClient.class, () -> mock(CosmosClient.class))
                .withBean(CosmosProperties.class, CosmosProperties::new)
                .withBean(CosmosCatalogoStore.class, () -> mock(CosmosCatalogoStore.class))
                .withBean(EstabelecimentoComercialStore.class, () -> mock(EstabelecimentoComercialStore.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(CosmosResourceInitializer.class)
                        .hasSingleBean(CosmosSeedDataLoader.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import({CosmosResourceInitializer.class, CosmosSeedDataLoader.class})
    static class RunnersConfiguration {
    }
}
