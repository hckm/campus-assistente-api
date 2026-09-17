package br.edu.usc.campusiachatbot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodexCliProcessRunnerTest {

    @TempDir
    Path diretorio;

    private final CodexCliProcessRunner runner = new CodexCliProcessRunner();

    @Test
    void deveInformarFalhaDoProcessoSemExporSaida() {
        assertThatThrownBy(() -> runner.executar(
                comando("fail"),
                diretorio,
                "",
                Duration.ofSeconds(5),
                4096
        )).isInstanceOfSatisfying(CodexCliException.class, exception -> {
            assertThat(exception.reason()).isEqualTo(CodexCliException.Reason.PROCESS_FAILURE);
            assertThat(exception.getMessage()).contains("codigo 7");
        });
    }

    @Test
    void deveEncerrarProcessoQuandoExcederTimeout() throws Exception {
        Path arquivoPid = diretorio.resolve("process.pid");

        assertThatThrownBy(() -> runner.executar(
                comando("sleep", arquivoPid.toString()),
                diretorio,
                "",
                Duration.ofSeconds(2),
                4096
        )).isInstanceOfSatisfying(CodexCliException.class, exception ->
                assertThat(exception.reason()).isEqualTo(CodexCliException.Reason.TIMEOUT));

        assertThat(Files.readString(arquivoPid)).isNotBlank();
        long pid = Long.parseLong(Files.readString(arquivoPid));
        assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
    }

    @Test
    void deveEncerrarProcessoQuandoSaidaExcederLimite() {
        assertThatThrownBy(() -> runner.executar(
                comando("output", "8192"),
                diretorio,
                "",
                Duration.ofSeconds(5),
                1024
        )).isInstanceOfSatisfying(CodexCliException.class, exception ->
                assertThat(exception.reason()).isEqualTo(CodexCliException.Reason.OUTPUT_LIMIT));
    }

    @Test
    void deveAplicarTimeoutEnquantoEscritaNoStdinEstiverBloqueada() throws Exception {
        Path arquivoPid = diretorio.resolve("stdin-process.pid");
        long inicio = System.nanoTime();

        assertThatThrownBy(() -> runner.executar(
                comando("ignore-stdin", arquivoPid.toString()),
                diretorio,
                "x".repeat(16384),
                Duration.ofSeconds(1),
                4096
        )).isInstanceOfSatisfying(CodexCliException.class, exception ->
                assertThat(exception.reason()).isEqualTo(CodexCliException.Reason.TIMEOUT));

        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(3));
        long pid = Long.parseLong(Files.readString(arquivoPid));
        assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
    }

    @RepeatedTest(5)
    void deveAplicarDeadlineNaDrenagemEEncerrarFilhoRastreadoAposSaidaDoPai() throws Exception {
        Path arquivoPidFilho = diretorio.resolve("child-process.pid");
        long inicio = System.nanoTime();

        assertThatThrownBy(() -> runner.executar(
                comando("parent-with-child", arquivoPidFilho.toString()),
                diretorio,
                "",
                Duration.ofSeconds(1),
                4096
        )).isInstanceOfSatisfying(CodexCliException.class, exception ->
                assertThat(exception.reason()).isEqualTo(CodexCliException.Reason.TIMEOUT));

        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(3));
        long pidFilho = Long.parseLong(Files.readString(arquivoPidFilho));
        assertThat(ProcessHandle.of(pidFilho).map(ProcessHandle::isAlive).orElse(false)).isFalse();
    }

    @Test
    void deveAplicarLimiteAgregadoEntreStdoutEStderr() {
        assertThatThrownBy(() -> runner.executar(
                comando("combined-output", "800"),
                diretorio,
                "",
                Duration.ofSeconds(5),
                1024
        )).isInstanceOfSatisfying(CodexCliException.class, exception ->
                assertThat(exception.reason()).isEqualTo(CodexCliException.Reason.OUTPUT_LIMIT));
    }

    private List<String> comando(String... argumentos) {
        String java = Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java"
        ).toString();
        List<String> comando = new java.util.ArrayList<>();
        comando.add(java);
        comando.add("-cp");
        comando.add(System.getProperty("java.class.path"));
        comando.add(CodexCliProcessFixture.class.getName());
        comando.addAll(List.of(argumentos));
        return comando;
    }
}
