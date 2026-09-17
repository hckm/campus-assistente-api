package br.edu.usc.campusiachatbot.client;

import br.edu.usc.campusiachatbot.config.CepLookupProperties;
import br.edu.usc.campusiachatbot.dto.CepLookupResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class CepLookupClient {

    private final CepLookupProperties properties;
    private final RestClient restClient;

    public CepLookupClient(CepLookupProperties properties, RestClient.Builder builder) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(properties.resolvedTimeoutSeconds());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        this.restClient = builder
                .baseUrl(properties.resolvedBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public Optional<CepLookupResponseDTO> consultar(String cep) {
        if (!properties.isEnabled() || cep == null || cep.isBlank()) {
            return Optional.empty();
        }

        try {
            CepLookupResponseDTO response = restClient.get()
                    .uri("/ws/{cep}/json/", cep)
                    .retrieve()
                    .body(CepLookupResponseDTO.class);

            if (response == null || !response.valido()) {
                return Optional.empty();
            }

            return Optional.of(response);
        } catch (RestClientException exception) {
            log.warn("Falha ao consultar CEP {}", cep, exception);
            return Optional.empty();
        }
    }

    public List<CepLookupResponseDTO> buscarPorEndereco(String uf, String cidade, String logradouro) {
        if (!properties.isEnabled()
                || uf == null || uf.isBlank()
                || cidade == null || cidade.isBlank()
                || logradouro == null || logradouro.isBlank()) {
            return List.of();
        }

        if (cidade.trim().length() < 3 || logradouro.trim().length() < 3) {
            return List.of();
        }

        try {
            CepLookupResponseDTO[] response = restClient.get()
                    .uri("/ws/{uf}/{cidade}/{logradouro}/json/", uf.trim(), cidade.trim(), logradouro.trim())
                    .retrieve()
                    .body(CepLookupResponseDTO[].class);

            if (response == null) {
                return List.of();
            }

            return Arrays.stream(response)
                    .filter(item -> item != null && item.valido())
                    .toList();
        } catch (RestClientException exception) {
            log.warn(
                    "Falha ao buscar CEP por endereco. uf={}, cidade={}, logradouro={}",
                    uf,
                    cidade,
                    logradouro,
                    exception
            );
            return List.of();
        }
    }
}
