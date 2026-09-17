package br.edu.usc.campusiachatbot.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

class CosmosProfileConfigurationTest {

    @Test
    void deveUsarEmuladorSomenteQuandoPerfilLocalTambemEstiverAtivo() {
        SpringApplication application = application("local", "cosmos");
        try (ConfigurableApplicationContext context = application.run(
                "--COSMOS_EMULATOR_ENDPOINT=https://localhost:8081",
                "--COSMOS_EMULATOR_KEY=chave-emulador"
        )) {
            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty("azure.cosmos.endpoint"))
                    .isEqualTo("https://localhost:8081");
            assertThat(environment.getProperty("azure.cosmos.autenticacao"))
                    .isEqualTo("EMULATOR");
            assertThat(environment.getProperty("azure.cosmos.emulator-key"))
                    .isEqualTo("chave-emulador");
            assertThat(environment.getProperty("azure.cosmos.provisioning.enabled")).isEqualTo("true");
            assertThat(environment.getProperty("azure.cosmos.seed.enabled")).isEqualTo("true");
        }
    }

    @Test
    void deveUsarEndpointCloudEIdentidadeGerenciadaNoPerfilProdCosmos() {
        SpringApplication application = application("prod", "cosmos");
        try (ConfigurableApplicationContext context = application.run(
                "--AZURE_COSMOS_ENDPOINT=https://conta.documents.azure.com:443/"
        )) {
            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty("azure.cosmos.endpoint"))
                    .isEqualTo("https://conta.documents.azure.com:443/");
            assertThat(environment.getProperty("azure.cosmos.autenticacao"))
                    .isEqualTo("MANAGED_IDENTITY");
            assertThat(environment.getProperty("azure.cosmos.emulator-key")).isEmpty();
            assertThat(environment.getProperty("azure.cosmos.provisioning.enabled")).isEqualTo("false");
            assertThat(environment.getProperty("azure.cosmos.seed.enabled")).isEqualTo("false");
        }
    }

    private SpringApplication application(String... profiles) {
        SpringApplication application = new SpringApplication(EmptyConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles(profiles);
        return application;
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfiguration {
    }
}
