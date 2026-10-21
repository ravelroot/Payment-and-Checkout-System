package dev.velo.catalog;

import dev.velo.common.PageResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalog;
    private final CategoryRepository categories;
    private final ProductRepository products;

    public CatalogController(CatalogService catalog, CategoryRepository categories, ProductRepository products) {
        this.catalog = catalog;
        this.categories = categories;
        this.products = products;
    }

    @GetMapping("/categories")
    public List<CatalogDtos.CategoryView> categories() {
        return categories.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(c -> new CatalogDtos.CategoryView(c.getId(), c.getName(), c.getSlug(), c.getDescription(), c.getSortOrder(),
                        c.isActive(), products.countByCategoryId(c.getId())))
                .toList();
    }

    @GetMapping("/products")
    public PageResponse<CatalogDtos.ProductCard> products(@RequestParam(required = false) String q,
                                                          @RequestParam(required = false) String category,
                                                          @RequestParam(required = false) BigDecimal minPrice,
                                                          @RequestParam(required = false) BigDecimal maxPrice,
                                                          @RequestParam(required = false) String sort,
                                                          @RequestParam(defaultValue = "false") boolean onlyAvailable,
                                                          @RequestParam(required = false) Boolean featured,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "24") int size) {
        return catalog.search(new CatalogService.Search(q, category, minPrice, maxPrice, sort, onlyAvailable, false, featured,
                page, size)).map(CatalogService::card);
    }

    @GetMapping("/products/{slug}")
    public CatalogDtos.ProductDetail product(@PathVariable String slug) {
        return CatalogService.detail(catalog.bySlug(slug, false));
    }
}
