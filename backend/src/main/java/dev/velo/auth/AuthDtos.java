package dev.velo.auth;

import dev.velo.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank(message = "Informe o e-mail.") String email,
                               @NotBlank(message = "Informe a senha.") String password) {
    }

    public record RegisterRequest(@NotBlank(message = "Informe seu nome.") @Size(max = 120) String fullName,
                                  @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 160) String email,
                                  @NotBlank(message = "Crie uma senha.") @Size(min = 8, max = 72, message = "A senha precisa ter de 8 a 72 caracteres.") String password) {
    }

    public record Me(Long id, String fullName, String email, UserRole role) {
    }

    public record SessionInfo(String token, Me user, Instant idleExpiresAt, Instant expiresAt, int idleMinutes, int maxMinutes) {
    }
}
