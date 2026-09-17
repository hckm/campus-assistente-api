package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.config.CodexCliProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CodexCliExecutor {

    private static final String RESPONSE_SCHEMA = serializarSchema();

    private static final List<String> DISABLED_FEATURES = List.of(
            "shell_tool",
            "unified_exec",
            "shell_snapshot",
            "apps",
            "plugins",
            "browser_use",
            "browser_use_external",
            "browser_use_full_cdp_access",
            "computer_use",
            "image_generation",
            "view_image",
            "multi_agent",
            "multi_agent_v2",
            "goals",
            "sleep_tool",
            "workspace_dependencies",
            "code_mode_host",
            "tool_suggest",
            "auth_elicitation",
            "request_permissions_tool",
            "hooks",
            "skill_search",
            "skill_mcp_dependency_install",
            "standalone_web_search",
            "artifact",
            "recommended_plugins",
            "memories"
    );

    private final CodexCliProperties properties;
    private final CodexCliProcessRunner processRunner;

    public CodexCliExecutor(CodexCliProperties properties, CodexCliProcessRunner processRunner) {
        this.properties = properties;
        this.processRunner = processRunner;
    }

    private static String serializarSchema() {
        try {
            return new ObjectMapper().writeValueAsString(InterpretacaoIaSchema.criar());
        } catch (JsonProcessingException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    public String executar(String prompt) {
        Path diretorio = null;
        try {
            diretorio = Files.createTempDirectory("campus-ia-codex-").toAbsolutePath().normalize();
            Path schema = diretorio.resolve("response-schema.json");
            Path resposta = diretorio.resolve("response.json");
            Files.writeString(schema, RESPONSE_SCHEMA, StandardCharsets.UTF_8);

            processRunner.executar(
                    construirComando(diretorio, schema, resposta),
                    diretorio,
                    prompt,
                    properties.timeout(),
                    properties.maxOutputBytes()
            );

            if (!Files.isRegularFile(resposta)) {
                throw new CodexCliException(
                        CodexCliException.Reason.INVALID_OUTPUT,
                        "Codex CLI nao produziu o arquivo de resposta"
                );
            }
            return lerRespostaLimitada(resposta);
        } catch (CodexCliException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new CodexCliException(
                    CodexCliException.Reason.PROCESS_FAILURE,
                    "Falha ao preparar ou ler a execucao do Codex CLI",
                    exception
            );
        } finally {
            apagarDiretorio(diretorio);
        }
    }

    private String lerRespostaLimitada(Path resposta) throws IOException {
        if (Files.size(resposta) > properties.maxOutputBytes()) {
            throw limiteRespostaExcedido();
        }

        try (InputStream stream = Files.newInputStream(resposta);
             ByteArrayOutputStream conteudo = new ByteArrayOutputStream(
                     Math.min(properties.maxOutputBytes(), 8192))) {
            byte[] buffer = new byte[4096];
            long total = 0;
            int lidos;
            while ((lidos = stream.read(buffer)) != -1) {
                total += lidos;
                if (total > properties.maxOutputBytes()) {
                    throw limiteRespostaExcedido();
                }
                conteudo.write(buffer, 0, lidos);
            }
            return conteudo.toString(StandardCharsets.UTF_8);
        }
    }

    private CodexCliException limiteRespostaExcedido() {
        return new CodexCliException(
                CodexCliException.Reason.OUTPUT_LIMIT,
                "Resposta do Codex CLI excedeu o limite configurado"
        );
    }

    List<String> construirComando(Path diretorio, Path schema, Path resposta) {
        List<String> comando = new ArrayList<>();
        comando.add(properties.executable());
        comando.add("exec");
        comando.add("--ignore-user-config");
        comando.add("--ignore-rules");
        comando.add("--strict-config");
        comando.add("--ephemeral");
        comando.add("--skip-git-repo-check");
        comando.add("--sandbox");
        comando.add("read-only");
        DISABLED_FEATURES.forEach(feature -> {
            comando.add("--disable");
            comando.add(feature);
        });
        comando.add("-c");
        comando.add("web_search=\"disabled\"");
        comando.add("-c");
        comando.add("approval_policy=\"never\"");
        comando.add("-c");
        comando.add("mcp_servers={}");
        comando.add("-c");
        comando.add("history.persistence=\"none\"");
        comando.add("-c");
        comando.add("apps._default.enabled=false");
        comando.add("-c");
        comando.add("agents.enabled=false");
        comando.add("-c");
        comando.add("memories.use_memories=false");
        comando.add("-c");
        comando.add("analytics.enabled=false");
        comando.add("-c");
        comando.add("feedback.enabled=false");
        comando.add("-c");
        comando.add("otel.exporter=\"none\"");
        comando.add("--color");
        comando.add("never");
        comando.add("--cd");
        comando.add(diretorio.toString());
        comando.add("--output-schema");
        comando.add(schema.toString());
        comando.add("--output-last-message");
        comando.add(resposta.toString());
        if (properties.hasModel()) {
            comando.add("--model");
            comando.add(properties.model());
        }
        comando.add("-");
        return List.copyOf(comando);
    }

    private void apagarDiretorio(Path diretorio) {
        if (diretorio == null || !Files.exists(diretorio)) {
            return;
        }

        try (var arquivos = Files.walk(diretorio)) {
            arquivos.sorted(Comparator.reverseOrder()).forEach(this::apagarArquivo);
        } catch (IOException ignored) {
        }
    }

    private void apagarArquivo(Path arquivo) {
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException ignored) {
        }
    }
}
