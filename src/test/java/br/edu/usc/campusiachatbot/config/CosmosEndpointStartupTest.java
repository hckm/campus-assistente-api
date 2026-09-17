package br.edu.usc.campusiachatbot.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mockConstruction;

class CosmosEndpointStartupTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "AccountEndpoint=https://localhost:8081;AccountKey=%s;",
            "https://usuario:%s@localhost:8081",
            "https://usuario:%s@localhost:8081/caminho invalido",
            "https://localhost:8081?AccountKey=%s"
    })
    void deveRejeitarEndpointSemExporSegredoNaFalhaOuNoRelatorioRealDoBoot(String formatoEndpoint) {
        String marcador = "QA_SYNTHETIC_SECRET";
        String endpoint = formatoEndpoint.formatted(marcador);
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();

        try (var builders = mockConstruction(CosmosClientBuilder.class);
             var credenciais = mockConstruction(ManagedIdentityCredentialBuilder.class)) {
            Throwable falha = catchThrowable(() -> {
                try (var context = new SpringApplicationBuilder(CosmosConfig.class)
                        .web(WebApplicationType.NONE)
                        .registerShutdownHook(false)
                        .initializers(inicializando -> logger.addAppender(appender))
                        .run("--azure.cosmos.enabled=true", "--azure.cosmos.endpoint=" + endpoint,
                                "--azure.cosmos.emulator-key=" + marcador, "--spring.main.banner-mode=off")) {
                    assertThat(context.isActive()).isFalse();
                }
            });

            assertThat(falha).isInstanceOf(RuntimeException.class);
            assertThat(builders.constructed()).isEmpty();
            assertThat(credenciais.constructed()).isEmpty();
            StringWriter cadeiaDaFalha = new StringWriter();
            falha.printStackTrace(new PrintWriter(cadeiaDaFalha));
            assertThat(cadeiaDaFalha.toString()).doesNotContain(marcador);
            String logsRenderizados = appender.list.stream()
                    .map(evento -> evento.getFormattedMessage() + (evento.getThrowableProxy() == null
                            ? "" : ThrowableProxyUtil.asString(evento.getThrowableProxy())))
                    .reduce("", String::concat);
            assertThat(logsRenderizados).contains("APPLICATION FAILED TO START", "endpoint deve usar HTTPS")
                    .doesNotContain(marcador);
            assertThat(appender.list).anySatisfy(evento -> {
                assertThat(evento.getLoggerName()).isEqualTo(
                        "org.springframework.boot.diagnostics.LoggingFailureAnalysisReporter");
                assertThat(evento.getFormattedMessage()).contains("APPLICATION FAILED TO START");
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
