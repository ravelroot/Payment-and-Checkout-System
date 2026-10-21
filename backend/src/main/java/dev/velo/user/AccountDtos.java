package dev.velo.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record Profile(Long id, String fullName, String email, UserRole role, String phone, String document,
                          Instant createdAt, Instant lastLoginAt) {
    }

    public record ProfileInput(@NotBlank(message = "Informe seu nome.") @Size(max = 120) String fullName,
                               @Size(max = 30) String phone,
                               @Size(max = 20) String document) {
    }

    public record PasswordInput(@NotBlank(message = "Informe a senha atual.") String currentPassword,
                                @NotBlank(message = "Informe a nova senha.") @Size(min = 8, max = 72, message = "A senha precisa ter de 8 a 72 caracteres.") String newPassword) {
    }

    public record AddressView(Long id, String label, String recipient, String zipCode, String street, String number,
                              String complement, String district, String city, String state, boolean isDefault) {
    }

    public record AddressInput(@Size(max = 40) String label,
                               @NotBlank(message = "Informe quem recebe.") @Size(max = 120) String recipient,
                               @NotBlank(message = "Informe o CEP.") @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP inválido.") String zipCode,
                               @NotBlank(message = "Informe a rua.") @Size(max = 160) String street,
                               @NotBlank(message = "Informe o número.") @Size(max = 20) String number,
                               @Size(max = 80) String complement,
                               @NotBlank(message = "Informe o bairro.") @Size(max = 80) String district,
                               @NotBlank(message = "Informe a cidade.") @Size(max = 80) String city,
                               @NotBlank(message = "Informe a UF.") @Pattern(regexp = "[A-Za-z]{2}", message = "UF inválida.") String state,
                               Boolean isDefault) {
    }

    public record AdminUserView(Long id, String fullName, String email, UserRole role, boolean active, String phone,
                                String document, Instant createdAt, Instant lastLoginAt, long orders) {
    }

    public record AdminUserInput(@NotBlank(message = "Informe o nome.") @Size(max = 120) String fullName,
                                 @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 160) String email,
                                 @NotNull UserRole role,
                                 Boolean active,
                                 @Size(max = 30) String phone,
                                 @Size(max = 20) String document,
                                 @Size(max = 72) String password) {
    }

    public record ResetPassword(@NotBlank @Size(min = 8, max = 72, message = "A senha precisa ter de 8 a 72 caracteres.") String password) {
    }
}
