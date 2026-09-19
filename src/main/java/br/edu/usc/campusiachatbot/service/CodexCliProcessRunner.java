package br.edu.usc.campusiachatbot.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

@Component
public class CodexCliProcessRunner {

    public void executar(
            List<String> comando,
            Path diretorio,
            String entrada,
            Duration timeout,
            int limiteSaidaBytes
    ) {
        long deadline = calcularDeadline(timeout);
        Process processo;
        try {
            processo = new ProcessBuilder(comando)
                    .directory(diretorio.toFile())
                    .redirectErrorStream(false)
                    .start();
        } catch (IOException exception) {
            throw new CodexCliException(
                    CodexCliException.Reason.START_FAILURE,
                    "Nao foi possivel iniciar o Codex CLI",
                    exception
            );
        }

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicLong totalSaida = new AtomicLong();
        AtomicBoolean rastreamentoAtivo = new AtomicBoolean(true);
        Map<Long, ProcessoRastreado> descendentes = new ConcurrentHashMap<>();
        registrarDescendentes(processo, descendentes);

        Future<?> rastreamento = executor.submit(
                () -> rastrearDescendentes(processo, descendentes, rastreamentoAtivo));
        Future<?> saida = executor.submit(
                () -> drenar(processo.getInputStream(), limiteSaidaBytes, totalSaida, processo));
        Future<?> erro = executor.submit(
                () -> drenar(processo.getErrorStream(), limiteSaidaBytes, totalSaida, processo));
        Future<?> escrita = executor.submit(() -> escrever(processo.getOutputStream(), entrada));

        try {
            aguardarProcesso(processo, deadline);
            aguardarTarefa(saida, deadline);
            aguardarTarefa(erro, deadline);
            aguardarTarefa(escrita, deadline);

            if (processo.exitValue() != 0) {
                throw new CodexCliException(
                        CodexCliException.Reason.PROCESS_FAILURE,
                        "Codex CLI encerrou com codigo " + processo.exitValue()
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CodexCliException(
                    CodexCliException.Reason.INTERRUPTED,
                    "Execucao do Codex CLI interrompida",
                    exception
            );
        } finally {
            rastreamentoAtivo.set(false);
            rastreamento.cancel(true);
            registrarDescendentes(processo, descendentes);
            encerrarArvore(processo, descendentes);
            saida.cancel(true);
            erro.cancel(true);
            escrita.cancel(true);
            fecharStreamsAssincrono(processo);
            executor.shutdownNow();
        }
    }

    private long calcularDeadline(Duration timeout) {
        long agora = System.nanoTime();
        try {
            return Math.addExact(agora, timeout.toNanos());
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private void aguardarProcesso(Process processo, long deadline) throws InterruptedException {
        long restante = tempoRestante(deadline);
        if (restante == 0 || !processo.waitFor(restante, TimeUnit.NANOSECONDS)) {
            throw timeout();
        }
    }

    private void aguardarTarefa(Future<?> tarefa, long deadline) throws InterruptedException {
        long restante = tempoRestante(deadline);
        if (restante == 0) {
            throw timeout();
        }

        try {
            tarefa.get(restante, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            throw timeout();
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof CodexCliException codexCliException) {
                throw codexCliException;
            }
            throw new CodexCliException(
                    CodexCliException.Reason.PROCESS_FAILURE,
                    "Falha durante a comunicacao com o Codex CLI",
                    exception.getCause()
            );
        }
    }

    private long tempoRestante(long deadline) {
        return Math.max(0, deadline - System.nanoTime());
    }

    private CodexCliException timeout() {
        return new CodexCliException(
                CodexCliException.Reason.TIMEOUT,
                "Codex CLI excedeu o timeout configurado"
        );
    }

    private void escrever(OutputStream stream, String entrada) {
        try (stream) {
            stream.write(entrada.getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new CodexCliException(
                    CodexCliException.Reason.PROCESS_FAILURE,
                    "Falha ao enviar a entrada ao Codex CLI",
                    exception
            );
        }
    }

    private void drenar(
            InputStream stream,
            int limiteSaidaBytes,
            AtomicLong totalSaida,
            Process processo
    ) {
        try (stream) {
            byte[] buffer = new byte[4096];
            int lidos;
            while ((lidos = stream.read(buffer)) != -1) {
                if (totalSaida.addAndGet(lidos) > limiteSaidaBytes) {
                    processo.destroyForcibly();
                    throw new CodexCliException(
                            CodexCliException.Reason.OUTPUT_LIMIT,
                            "Codex CLI excedeu o limite agregado de saida configurado"
                    );
                }
            }
        } catch (IOException exception) {
            throw new CodexCliException(
                    CodexCliException.Reason.PROCESS_FAILURE,
                    "Falha ao capturar a saida do Codex CLI",
                    exception
            );
        }
    }

    private void rastrearDescendentes(
            Process processo,
            Map<Long, ProcessoRastreado> descendentes,
            AtomicBoolean ativo
    ) {
        while (ativo.get() && processo.isAlive() && !Thread.currentThread().isInterrupted()) {
            registrarDescendentes(processo, descendentes);
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        registrarDescendentes(processo, descendentes);
    }

    private void registrarDescendentes(Process processo, Map<Long, ProcessoRastreado> descendentes) {
        try {
            processo.descendants().forEach(handle ->
                    descendentes.compute(handle.pid(), (pid, atual) -> novoRastreamento(handle, atual)));
        } catch (RuntimeException ignored) {
        }
    }

    private ProcessoRastreado novoRastreamento(
            ProcessHandle handle,
            ProcessoRastreado atual
    ) {
        int profundidade = profundidade(handle);
        if (atual != null && atual.profundidade() >= profundidade) {
            return atual;
        }
        return new ProcessoRastreado(
                handle.pid(),
                handle.info().startInstant(),
                handle.info().commandLine(),
                profundidade
        );
    }

    private void encerrarArvore(Process processo, Map<Long, ProcessoRastreado> descendentes) {
        List<ProcessoRastreado> processos = new ArrayList<>(descendentes.values());
        processos.sort(Comparator.comparingInt(ProcessoRastreado::profundidade).reversed());
        processos.forEach(processoRastreado -> destruir(processoRastreado, false));
        processo.destroy();
        aguardarEncerramento(processo, 200);
        processos.forEach(processoRastreado -> destruir(processoRastreado, true));
        aguardarEncerramento(processos, 300);
        if (processo.isAlive()) {
            processo.destroyForcibly();
        }
        aguardarEncerramento(processo, 300);
    }

    private void destruir(ProcessoRastreado processoRastreado, boolean forcado) {
        ProcessHandle.of(processoRastreado.pid())
                .filter(ProcessHandle::isAlive)
                .filter(processoRastreado::corresponde)
                .ifPresent(handle -> {
                    if (forcado) {
                        handle.destroyForcibly();
                    } else {
                        handle.destroy();
                    }
                });
    }

    private void aguardarEncerramento(Process processo, long milissegundos) {
        if (!processo.isAlive()) {
            return;
        }
        try {
            processo.waitFor(milissegundos, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void aguardarEncerramento(List<ProcessoRastreado> processos, long milissegundos) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milissegundos);
        while (processos.stream().anyMatch(this::estaVivo)
                && System.nanoTime() < deadline
                && !Thread.currentThread().isInterrupted()) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        }
    }

    private boolean estaVivo(ProcessoRastreado processoRastreado) {
        return ProcessHandle.of(processoRastreado.pid())
                .filter(ProcessHandle::isAlive)
                .filter(processoRastreado::corresponde)
                .isPresent();
    }

    private void fecharStreamsAssincrono(Process processo) {
        Thread.ofVirtual().start(() -> fechar(processo.getOutputStream()));
        Thread.ofVirtual().start(() -> fechar(processo.getInputStream()));
        Thread.ofVirtual().start(() -> fechar(processo.getErrorStream()));
    }

    private void fechar(AutoCloseable stream) {
        try {
            stream.close();
        } catch (Exception ignored) {
        }
    }

    private int profundidade(ProcessHandle processo) {
        int profundidade = 0;
        ProcessHandle atual = processo;
        while (atual.parent().isPresent()) {
            profundidade++;
            atual = atual.parent().orElseThrow();
        }
        return profundidade;
    }

    private record ProcessoRastreado(
            long pid,
            Optional<Instant> inicio,
            Optional<String> linhaComando,
            int profundidade
    ) {

        private boolean corresponde(ProcessHandle handle) {
            if (inicio.isPresent()) {
                return handle.info().startInstant().map(inicio.get()::equals).orElse(false);
            }
            return linhaComando.isPresent()
                    && handle.info().commandLine().map(linhaComando.get()::equals).orElse(false);
        }
    }
}
