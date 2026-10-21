package dev.velo.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {

    private CartDtos() {
    }

    public record Line(Long productId, String slug, String sku, String name, String imageUrl, BigDecimal unitPrice,
                       int quantity, BigDecimal lineTotal, int stock, boolean available) {
    }

    public record View(List<Line> items, int count, BigDecimal subtotal, BigDecimal shipping, BigDecimal discount,
                       BigDecimal total, String couponCode, String couponMessage, BigDecimal freeShippingAbove) {
    }

    public record AddItem(@NotNull Long productId, @Min(1) @Max(99) Integer quantity) {
    }

    public record SetQuantity(@NotNull @Min(0) @Max(99) Integer quantity) {
    }
}
