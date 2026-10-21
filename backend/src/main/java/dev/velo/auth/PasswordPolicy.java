package dev.velo.auth;

import dev.velo.common.ApiException;
import java.util.Locale;

public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void check(String password, String email, String name) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw ApiException.badRequest("WEAK_PASSWORD", "A senha precisa ter de 8 a 72 caracteres.");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Use letras e números na senha.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        String local = email == null ? "" : email.split("@")[0].toLowerCase(Locale.ROOT);
        if (local.length() >= 4 && lower.contains(local)) {
            throw ApiException.badRequest("WEAK_PASSWORD", "A senha não pode conter o seu e-mail.");
        }
        if (name != null) {
            for (String part : name.toLowerCase(Locale.ROOT).split("\\s+")) {
                if (part.length() >= 4 && lower.contains(part)) {
                    throw ApiException.badRequest("WEAK_PASSWORD", "A senha não pode conter o seu nome.");
                }
            }
        }
    }
}
