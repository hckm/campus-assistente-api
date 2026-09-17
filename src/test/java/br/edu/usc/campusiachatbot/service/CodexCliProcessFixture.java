package br.edu.usc.campusiachatbot.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CodexCliProcessFixture {

    public static void main(String[] args) throws Exception {
        if ("sleep".equals(args[0])) {
            Files.writeString(
                    Path.of(args[1]),
                    Long.toString(ProcessHandle.current().pid()),
                    StandardCharsets.UTF_8
            );
            Thread.sleep(30000);
            return;
        }
        if ("fail".equals(args[0])) {
            System.exit(7);
        }
        if ("output".equals(args[0])) {
            System.out.print("x".repeat(Integer.parseInt(args[1])));
        }
        if ("ignore-stdin".equals(args[0])) {
            Files.writeString(
                    Path.of(args[1]),
                    Long.toString(ProcessHandle.current().pid()),
                    StandardCharsets.UTF_8
            );
            Thread.sleep(30000);
        }
        if ("combined-output".equals(args[0])) {
            int tamanho = Integer.parseInt(args[1]);
            System.out.print("o".repeat(tamanho));
            System.err.print("e".repeat(tamanho));
        }
        if ("parent-with-child".equals(args[0])) {
            Process filho = new ProcessBuilder(comandoJava("child-sleep"))
                    .inheritIO()
                    .start();
            Files.writeString(
                    Path.of(args[1]),
                    Long.toString(filho.pid()),
                    StandardCharsets.UTF_8
            );
        }
        if ("child-sleep".equals(args[0])) {
            Thread.sleep(30000);
        }
    }

    private static List<String> comandoJava(String operacao) {
        String java = Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java"
        ).toString();
        List<String> comando = new ArrayList<>();
        comando.add(java);
        comando.add("-cp");
        comando.add(System.getProperty("java.class.path"));
        comando.add(CodexCliProcessFixture.class.getName());
        comando.add(operacao);
        return comando;
    }
}
