package dev.velo.catalog;

import dev.velo.auth.CurrentUser;
import dev.velo.common.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminCatalogController {

    private final CatalogService catalog;
    private final CategoryRepository categories;
    private final ProductRepository products;

    public AdminCatalogController(CatalogService catalog, CategoryRepository categories, ProductRepository products) {
        this.catalog = catalog;
        this.categories = categories;
        this.products = products;
    }

    @GetMapping("/categories")
    public List<CatalogDtos.CategoryView> categories() {
        return categories.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(c -> new CatalogDtos.CategoryView(c.getId(), c.getName(), c.getSlug(), c.getDescription(), c.getSortOrder(),
                        c.isActive(), products.countByCategoryId(c.getId())))
                .toList();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public Category createCategory(@Valid @RequestBody CatalogDtos.CategoryInput in) {
        return catalog.saveCategory(null, in);
    }

    @PutMapping("/categories/{id}")
    public Category updateCategory(@PathVariable Long id, @Valid @RequestBody CatalogDtos.CategoryInput in) {
        return catalog.saveCategory(id, in);
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        catalog.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/products")
    public PageResponse<CatalogDtos.ProductDetail> products(@RequestParam(required = false) String q,
                                                            @RequestParam(required = false) String category,
                                                            @RequestParam(required = false) String sort,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return catalog.search(new CatalogService.Search(q, category, null, null, sort == null ? "name" : sort, false, true, null,
                page, size)).map(CatalogService::detail);
    }

    @GetMapping("/products/{id}")
    public CatalogDtos.ProductDetail product(@PathVariable Long id) {
        return CatalogService.detail(catalog.get(id));
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.ProductDetail create(@Valid @RequestBody CatalogDtos.ProductInput in) {
        Product p = catalog.save(null, in, CurrentUser.id());
        return CatalogService.detail(catalog.get(p.getId()));
    }

    @PutMapping("/products/{id}")
    public CatalogDtos.ProductDetail update(@PathVariable Long id, @Valid @RequestBody CatalogDtos.ProductInput in) {
        catalog.save(id, in, CurrentUser.id());
        return CatalogService.detail(catalog.get(id));
    }

    @PostMapping("/products/{id}/stock")
    public CatalogDtos.ProductDetail stock(@PathVariable Long id, @Valid @RequestBody CatalogDtos.StockInput in) {
        catalog.adjustStock(id, in, CurrentUser.id());
        return CatalogService.detail(catalog.get(id));
    }

    @GetMapping("/products/{id}/movements")
    public List<CatalogDtos.MovementView> movements(@PathVariable Long id) {
        return catalog.movements(id);
    }

    @DeleteMapping("/products/{id}")
    public java.util.Map<String, Boolean> delete(@PathVariable Long id) {
        return java.util.Map.of("deleted", catalog.delete(id));
    }
}
