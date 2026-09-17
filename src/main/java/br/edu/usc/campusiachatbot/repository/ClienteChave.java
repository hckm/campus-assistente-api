package br.edu.usc.campusiachatbot.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ClienteChave {

    private ClienteChave() {
    }

    public static String criar(String telefone) {
        String canonico = canonicalizar(telefone);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8));
            return "cliente:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponivel", exception);
        }
    }

    public static String canonicalizar(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            throw new IllegalArgumentException("telefoneCliente e obrigatorio");
        }
        String valor = telefone.trim();
        if (!valor.matches("\\+?[0-9()\\s.\\-]+")) {
            throw new IllegalArgumentException("telefoneCliente contem caracteres invalidos");
        }
        String canonico = valor.replaceAll("[()\\s.\\-]", "");
        String digitos = canonico.startsWith("+") ? canonico.substring(1) : canonico;
        if (digitos.length() < 8 || digitos.length() > 20 || !digitos.matches("[0-9]+")) {
            throw new IllegalArgumentException("telefoneCliente deve ter entre 8 e 20 digitos");
        }
        return canonico;
    }
}
