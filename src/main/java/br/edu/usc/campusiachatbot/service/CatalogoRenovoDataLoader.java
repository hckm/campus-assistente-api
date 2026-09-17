package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.domain.ProdutoCatalogo;
import br.edu.usc.campusiachatbot.store.CatalogoStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "catalogo", name = "backend", havingValue = "JPA", matchIfMissing = true)
public class CatalogoRenovoDataLoader implements ApplicationRunner {

    private static final String CATALOG_RESOURCE = "classpath:data/catalogo-renovo.csv";

    private final CatalogoStore catalogoStore;
    private final ResourceLoader resourceLoader;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Resource resource = resourceLoader.getResource(CATALOG_RESOURCE);
        if (!resource.exists()) {
            log.warn("Arquivo de carga do catalogo Renovo nao encontrado: {}", CATALOG_RESOURCE);
            return;
        }

        CatalogoRenovoImportacaoResult resultado = carregarProdutos(resource);
        catalogoStore.salvarTodos(resultado.produtos());
        log.info(
                "Catalogo Renovo carregado com {} produtos: {} inserts e {} updates",
                resultado.produtos().size(),
                resultado.inserts(),
                resultado.updates()
        );
    }

    private CatalogoRenovoImportacaoResult carregarProdutos(Resource resource) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(),
                StandardCharsets.UTF_8
        ))) {
            List<CatalogoRenovoImportado> importados = reader.lines()
                    .skip(1)
                    .filter(line -> !line.isBlank())
                    .map(this::toEntity)
                    .toList();

            List<ProdutoCatalogo> produtos = importados.stream()
                    .map(CatalogoRenovoImportado::produto)
                    .toList();
            long updates = importados.stream()
                    .filter(CatalogoRenovoImportado::existente)
                    .count();
            long inserts = produtos.size() - updates;

            return new CatalogoRenovoImportacaoResult(produtos, inserts, updates);
        } catch (IOException exception) {
            throw new IllegalStateException("Falha ao carregar arquivo do catalogo Renovo", exception);
        }
    }

    private CatalogoRenovoImportado toEntity(String line) {
        List<String> columns = parseCsvLine(line);
        if (columns.size() != 7) {
            throw new IllegalStateException("Linha invalida no catalogo Renovo: " + line);
        }

        Integer codigoCatalogo = Integer.valueOf(columns.get(0));
        ProdutoCatalogo existente = catalogoStore.buscarPorCodigoCatalogo(codigoCatalogo).orElse(null);
        ProdutoCatalogo produto = new ProdutoCatalogo(
                existente == null ? null : existente.id(),
                existente == null ? null : existente.legacyId(),
                codigoCatalogo,
                columns.get(3),
                columns.get(1),
                columns.get(2),
                new BigDecimal(columns.get(4)),
                toBigDecimalOrNull(columns.get(5)),
                toStringOrNull(columns.get(6))
        );

        return new CatalogoRenovoImportado(produto, existente != null);
    }

    private BigDecimal toBigDecimalOrNull(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private String toStringOrNull(String value) {
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

        columns.add(current.toString());
        return columns;
    }

    private record CatalogoRenovoImportado(ProdutoCatalogo produto, boolean existente) {
    }

    private record CatalogoRenovoImportacaoResult(List<ProdutoCatalogo> produtos, long inserts, long updates) {
    }
}
