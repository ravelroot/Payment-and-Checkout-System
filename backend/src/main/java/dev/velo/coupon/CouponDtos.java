package dev.velo.coupon;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class CouponDtos {

    private CouponDtos() {
    }

    public record View(Long id, String code, String description, DiscountType type, BigDecimal value, BigDecimal minSubtotal,
                       Integer maxUses, int usedCount, Instant startsAt, Instant endsAt, boolean active) {
    }

    public record Input(@NotBlank(message = "Informe o código.") @Size(max = 30)
                        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "Use só letras, números, - e _.") String code,
                        @Size(max = 200) String description,
                        @NotNull DiscountType type,
                        @NotNull @DecimalMin(value = "0.01", message = "Informe um valor maior que zero.") BigDecimal value,
                        @DecimalMin("0.00") BigDecimal minSubtotal,
                        @Min(1) Integer maxUses,
                        Instant startsAt,
                        Instant endsAt,
                        Boolean active) {
    }
}
