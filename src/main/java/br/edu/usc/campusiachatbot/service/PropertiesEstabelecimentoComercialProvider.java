package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.config.EstabelecimentoProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "persistencia", name = "backend", havingValue = "JPA", matchIfMissing = true)
public class PropertiesEstabelecimentoComercialProvider implements EstabelecimentoComercialProvider {

    private final EstabelecimentoProperties properties;

    @Override
    public EstabelecimentoProperties obter() {
        return properties;
    }
}
