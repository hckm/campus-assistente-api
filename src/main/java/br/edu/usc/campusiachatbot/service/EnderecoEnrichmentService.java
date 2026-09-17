package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.client.CepLookupClient;
import br.edu.usc.campusiachatbot.config.EstabelecimentoProperties;
import br.edu.usc.campusiachatbot.dto.CepLookupResponseDTO;
import br.edu.usc.campusiachatbot.dto.EnderecoEnriquecidoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EnderecoEnrichmentService {

    private static final Pattern CEP_PATTERN = Pattern.compile("\\b\\d{5}-?\\d{3}\\b");
    private static final Pattern LOGRADOURO_PATTERN = Pattern.compile(
            "\\b(rua|avenida|av\\.?|alameda|travessa|pra(?:c|\\x{00E7})a|rodovia|estrada)\\s+([\\p{L}\\p{M}0-9 .'-]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private final CepLookupClient cepLookupClient;
    private final EstabelecimentoComercialProvider estabelecimentoComercialProvider;

    public EnderecoEnriquecidoDTO enriquecer(String mensagem) {
        String cep = extrairCep(mensagem);
        if (cep != null) {
            return cepLookupClient.consultar(cep)
                    .map(EnderecoEnriquecidoDTO::deCep)
                    .orElseGet(EnderecoEnriquecidoDTO::vazio);
        }

        String cidade = cidadeAtendidaMencionada(mensagem);
        String logradouro = extrairLogradouro(mensagem);
        if (cidade == null || logradouro == null || !estabelecimento().hasUf()) {
            return EnderecoEnriquecidoDTO.vazio();
        }

        List<CepLookupResponseDTO> ceps = cepLookupClient.buscarPorEndereco(
                estabelecimento().uf().trim(),
                cidade,
                logradouro
        );

        Optional<CepLookupResponseDTO> melhorResultado = ceps.stream()
                .filter(item -> cidadeAtendida(item.localidade()))
                .findFirst();

        return melhorResultado
                .map(EnderecoEnriquecidoDTO::deEndereco)
                .orElseGet(EnderecoEnriquecidoDTO::vazio);
    }

    private String extrairCep(String texto) {
        Matcher matcher = CEP_PATTERN.matcher(texto == null ? "" : texto);
        if (!matcher.find()) {
            return null;
        }

        return matcher.group().replaceAll("\\D", "");
    }

    private String extrairLogradouro(String mensagem) {
        Matcher matcher = LOGRADOURO_PATTERN.matcher(mensagem == null ? "" : mensagem);
        if (!matcher.find()) {
            return null;
        }

        String tipo = normalizarTipoLogradouro(matcher.group(1));
        String nome = matcher.group(2)
                .replaceFirst("\\s+\\d.*$", "")
                .replaceFirst("\\s+cep\\b.*$", "")
                .trim();

        for (String cidade : cidadesAtendidas()) {
            nome = nome.replaceAll("(?i)\\b" + Pattern.quote(cidade.trim()) + "\\b.*$", "").trim();
        }

        if (nome.length() < 3) {
            return null;
        }

        return (tipo + " " + nome).trim();
    }

    private String normalizarTipoLogradouro(String tipo) {
        String normalizado = normalizar(tipo);
        if (normalizado.equals("av") || normalizado.equals("av.")) {
            return "Avenida";
        }
        if (normalizado.equals("praca")) {
            return "Praca";
        }
        return Character.toUpperCase(tipo.charAt(0)) + tipo.substring(1).toLowerCase(Locale.ROOT);
    }

    private String cidadeAtendidaMencionada(String texto) {
        String textoNormalizado = normalizar(texto);
        for (String cidade : cidadesAtendidas()) {
            if (textoNormalizado.contains(normalizar(cidade))) {
                return cidade.trim();
            }
        }
        return null;
    }

    private boolean cidadeAtendida(String cidadeInformada) {
        String cidadeNormalizada = normalizar(cidadeInformada);
        return cidadesAtendidas().stream()
                .map(this::normalizar)
                .anyMatch(cidadeNormalizada::equals);
    }

    private List<String> cidadesAtendidas() {
        if (estabelecimento().cidadesAtendidas() == null) {
            return List.of();
        }

        return estabelecimento().cidadesAtendidas().stream()
                .filter(cidade -> cidade != null && !cidade.isBlank())
                .map(String::trim)
                .toList();
    }

    private String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT);
    }

    private EstabelecimentoProperties estabelecimento() {
        return estabelecimentoComercialProvider.obter();
    }
}
