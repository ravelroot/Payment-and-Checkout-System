package dev.velo.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(@NotNull(message = "Escolha a forma de pagamento.") PaymentMethod method,
                             @Min(1) @Max(12) Integer installments,
                             CardInput card) {
}
