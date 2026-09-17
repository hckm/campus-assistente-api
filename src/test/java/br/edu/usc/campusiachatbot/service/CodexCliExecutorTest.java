package br.edu.usc.campusiachatbot.service;

import br.edu.usc.campusiachatbot.config.CodexCliProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CodexCliExecutorTest {

    @Test
    void deveIsolarExecucaoDesativarFerramentasEEnviarPromptPeloStdin() throws Exception {
        CodexCliProcessRunner runner = mock(CodexCliProcessRunner.class);
        CodexCliExecutor executor = new CodexCliExecutor(
                new CodexCliProperties("codex", "", Duration.ofSeconds(12), 4096),
                runner
        );

        doAnswer(invocation -> {
            List<String> comando = invocation.getArgument(0);
            int indiceResposta = comando.indexOf("--output-last-message") + 1;
            Files.writeString(Path.of(comando.get(indiceResposta)), "{}", StandardCharsets.UTF_8);
            return null;
        }).when(runner).executar(any(), any(), any(), any(), anyInt());

        String resposta = executor.executar("mensagem ficticia");

        assertThat(resposta).isEqualTo("{}");
        ArgumentCaptor<List<String>> comando = ArgumentCaptor.captor();
        ArgumentCaptor<Path> diretorio = ArgumentCaptor.captor();
        verify(runner).executar(
                comando.capture(),
                diretorio.capture(),
                eq("mensagem ficticia"),
                eq(Duration.ofSeconds(12)),
                eq(4096)
        );
        assertThat(comando.getValue()).contains(
                "exec",
                "--ignore-user-config",
                "--ignore-rules",
                "--strict-config",
                "--ephemeral",
                "--skip-git-repo-check",
                "read-only",
                "shell_tool",
                "unified_exec",
                "apps",
                "plugins",
                "browser_use",
                "computer_use",
                "web_search=\"disabled\"",
                "approval_policy=\"never\"",
                "mcp_servers={}",
                "history.persistence=\"none\"",
                "agents.enabled=false",
                "memories.use_memories=false",
                "analytics.enabled=false",
                "feedback.enabled=false",
                "otel.exporter=\"none\"",
                "-"
        );
        assertThat(comando.getValue()).doesNotContain("mensagem ficticia");
        assertThat(diretorio.getValue().startsWith(Path.of(System.getProperty("java.io.tmpdir")))).isTrue();
        assertThat(Files.exists(diretorio.getValue())).isFalse();
    }

    @Test
    void deveLimitarLeituraDoArquivoJsonFinal() throws Exception {
        CodexCliProcessRunner runner = mock(CodexCliProcessRunner.class);
        CodexCliExecutor executor = new CodexCliExecutor(
                new CodexCliProperties("codex", "", Duration.ofSeconds(12), 1024),
                runner
        );

        doAnswer(invocation -> {
            List<String> comando = invocation.getArgument(0);
            int indiceResposta = comando.indexOf("--output-last-message") + 1;
            Files.writeString(
                    Path.of(comando.get(indiceResposta)),
                    "x".repeat(2048),
                    StandardCharsets.UTF_8
            );
            return null;
        }).when(runner).executar(any(), any(), any(), any(), anyInt());

        assertThatThrownBy(() -> executor.executar("mensagem ficticia"))
                .isInstanceOfSatisfying(CodexCliException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(CodexCliException.Reason.OUTPUT_LIMIT));
    }
}
