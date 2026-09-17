package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.config.CosmosProperties;
import br.edu.usc.campusiachatbot.domain.EstabelecimentoComercial;
import br.edu.usc.campusiachatbot.domain.ProdutoCatalogo;
import br.edu.usc.campusiachatbot.repository.CosmosCatalogoStore;
import br.edu.usc.campusiachatbot.store.EstabelecimentoComercialStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(1)
@RequiredArgsConstructor
@ConditionalOnBean({CosmosCatalogoStore.class, EstabelecimentoComercialStore.class})
@ConditionalOnProperty(prefix = "azure.cosmos.seed", name = "enabled", havingValue = "true")
public class CosmosSeedDataLoader implements ApplicationRunner {

    private static final String CATALOGO_RESOURCE = "classpath:data/catalogo-renovo.csv";
    private static final String ESTABELECIMENTO_RESOURCE = "classpath:data/estabelecimento-renovo.json";

    private final CosmosCatalogoStore catalogoStore;
    private final EstabelecimentoComercialStore estabelecimentoStore;
    private final CosmosProperties properties;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    @Override
    public void run(ApplicationArguments args) {
        EstabelecimentoComercial estabelecimento = carregarEstabelecimento();
        List<ProdutoCatalogo> produtos = carregarProdutos();
        estabelecimentoStore.criarSeAusente(estabelecimento);
        catalogoStore.criarTodosSeVazio(produtos);
    }

    private EstabelecimentoComercial carregarEstabelecimento() {
        Resource resource = resourceLoader.getResource(ESTABELECIMENTO_RESOURCE);
        if (!resource.exists()) {
            throw new IllegalStateException("Arquivo de carga do estabelecimento nao encontrado: " + ESTABELECIMENTO_RESOURCE);
        }
        try {
            EstabelecimentoComercial estabelecimento = objectMapper.readValue(
                    resource.getInputStream(), EstabelecimentoComercial.class
            );
            if (estabelecimento == null || !properties.getEstabelecimentoId().equals(estabelecimento.estabelecimentoId())) {
                throw new IllegalStateException("Arquivo de carga do estabelecimento possui identificador divergente");
            }
            return estabelecimento;
        } catch (IOException exception) {
            throw new IllegalStateException("Falha ao carregar arquivo do estabelecimento", exception);
        }
    }

    private List<ProdutoCatalogo> carregarProdutos() {
        Resource resource = resourceLoader.getResource(CATALOGO_RESOURCE);
        if (!resource.exists()) {
            throw new IllegalStateException("Arquivo de carga do catalogo nao encontrado: " + CATALOGO_RESOURCE);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8
        ))) {
            List<ProdutoCatalogo> produtos = reader.lines()
                    .skip(1)
                    .filter(line -> !line.isBlank())
                    .map(this::toProduto)
                    .toList();
            if (produtos.isEmpty()) {
                throw new IllegalStateException("Arquivo de carga do catalogo nao possui produtos");
            }
            return produtos;
        } catch (IOException exception) {
            throw new IllegalStateException("Falha ao carregar arquivo do catalogo", exception);
        }
    }

    private ProdutoCatalogo toProduto(String line) {
        List<String> columns = parseCsvLine(line);
        if (columns.size() != 7) {
            throw new IllegalStateException("Linha invalida no catalogo Renovo: " + line);
        }
        try {
            return new ProdutoCatalogo(
                    null,
                    null,
                    Integer.valueOf(columns.get(0)),
                    columns.get(3),
                    columns.get(1),
                    columns.get(2),
                    new BigDecimal(columns.get(4)),
                    decimalOuNulo(columns.get(5)),
                    textoOuNulo(columns.get(6))
            );
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Linha invalida no catalogo Renovo: " + line, exception);
        }
    }

    private BigDecimal decimalOuNulo(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private String textoOuNulo(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<String> parseCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append(character);
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        if (quoted) {
            throw new IllegalStateException("Linha invalida no catalogo Renovo: " + line);
        }
        columns.add(current.toString());
        return columns;
    }
}
