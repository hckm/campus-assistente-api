package br.edu.usc.campusiachatbot.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CosmosEmulatorEndpointValidatorTest {

    @Test
    void deveAceitarSomenteHttpsEmLoopbackComChave() {
        assertThat(CosmosEmulatorEndpointValidator.validar("https://localhost:8081", "chave-local"))
                .hasScheme("https")
                .hasHost("localhost")
                .hasPort(8081);
    }

    @Test
    void deveRejeitarEndpointExternoAntesDaConexao() {
        assertThatThrownBy(() -> CosmosEmulatorEndpointValidator.validar(
                "https://conta.documents.azure.com:443",
                "chave-local"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Autenticacao EMULATOR exige endpoint HTTPS de loopback local");
    }

    @Test
    void deveRejeitarHttpECredenciaisAusentes() {
        assertThatThrownBy(() -> CosmosEmulatorEndpointValidator.validar("http://localhost:8081", "chave-local"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CosmosEmulatorEndpointValidator.validar("https://localhost:8081", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Autenticacao EMULATOR exige emulator-key externa");
    }
}
