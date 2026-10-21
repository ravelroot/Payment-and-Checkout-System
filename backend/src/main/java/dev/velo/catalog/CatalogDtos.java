package dev.velo.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CategoryView(Long id, String name, String slug, String description, int sortOrder, boolean active, long products) {
    }

    public record CategoryInput(@NotBlank(message = "Informe o nome.") @Size(max = 80) String name,
                                @Size(max = 400) String description,
                                Integer sortOrder,
                                Boolean active) {
    }

    public record ProductCard(Long id, String sku, String name, String slug, BigDecimal price, BigDecimal compareAtPrice,
                              String imageUrl, int stock, boolean featured, String category, String categorySlug) {
    }

    public record ProductDetail(Long id, String sku, String name, String slug, String description, BigDecimal price,
                                BigDecimal compareAtPrice, String imageUrl, int stock, int lowStockThreshold, int weightGrams,
                                boolean active, boolean featured, Long categoryId, String category, String categorySlug,
                                Long version, Instant createdAt) {
    }

    public record ProductInput(@NotBlank(message = "Informe o nome.") @Size(max = 140) String name,
                               @Size(max = 40) String sku,
                               @Size(max = 2000) String description,
                               @NotNull(message = "Informe o preço.") @DecimalMin(value = "0.01", message = "O preço precisa ser maior que zero.") BigDecimal price,
                               @DecimalMin(value = "0.00") BigDecimal compareAtPrice,
                               Long categoryId,
                               @Size(max = 400) String imageUrl,
                               @Min(value = 0, message = "O estoque não pode ser negativo.") Integer stock,
                               @Min(0) Integer lowStockThreshold,
                               @Min(0) Integer weightGrams,
                               Boolean active,
                               Boolean featured,
                               Long version) {
    }

    public record StockInput(@NotNull MovementType type, @NotNull Integer quantity, @Size(max = 200) String reason) {
    }

    public record MovementView(Long id, MovementType type, int quantity, int stockAfter, String reason, Long orderId,
                               Instant createdAt) {
    }
}
