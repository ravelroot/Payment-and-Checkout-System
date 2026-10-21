package dev.velo.catalog;

import dev.velo.cart.CartItemRepository;
import dev.velo.common.ApiException;
import dev.velo.common.Money;
import dev.velo.common.PageResponse;
import dev.velo.common.Texts;
import dev.velo.common.Times;
import dev.velo.order.OrderItemRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    public record Search(String q, String category, BigDecimal minPrice, BigDecimal maxPrice, String sort,
                         boolean onlyAvailable, boolean includeInactive, Boolean featured, int page, int size) {
    }

    private final EntityManager em;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final InventoryMovementRepository movements;
    private final OrderItemRepository orderItems;
    private final CartItemRepository cartItems;
    private final Clock clock;

    public CatalogService(EntityManager em, ProductRepository products, CategoryRepository categories,
                          InventoryMovementRepository movements, OrderItemRepository orderItems, CartItemRepository cartItems,
                          Clock clock) {
        this.em = em;
        this.products = products;
        this.categories = categories;
        this.movements = movements;
        this.orderItems = orderItems;
        this.cartItems = cartItems;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<Product> search(Search s) {
        int size = Math.max(1, Math.min(s.size(), 100));
        int page = Math.max(0, s.page());
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Product> cq = cb.createQuery(Product.class);
        Root<Product> root = cq.from(Product.class);
        root.fetch("category", JoinType.LEFT);
        cq.select(root).where(predicates(cb, root, s));
        cq.orderBy(orders(cb, root, s.sort()));
        List<Product> items = em.createQuery(cq).setFirstResult(page * size).setMaxResults(size).getResultList();
        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<Product> countRoot = count.from(Product.class);
        count.select(cb.count(countRoot)).where(predicates(cb, countRoot, s));
        long total = em.createQuery(count).getSingleResult();
        return PageResponse.of(items, page, size, total);
    }

    private Predicate[] predicates(CriteriaBuilder cb, Root<Product> root, Search s) {
        List<Predicate> p = new ArrayList<>();
        if (!s.includeInactive()) {
            p.add(cb.isTrue(root.get("active")));
        }
        String like = Texts.like(s.q());
        if (like != null) {
            p.add(cb.or(cb.like(cb.lower(root.get("name")), like, '\\'), cb.like(cb.lower(root.get("sku")), like, '\\')));
        }
        if (s.category() != null && !s.category().isBlank()) {
            Join<Product, Category> c = root.join("category", JoinType.INNER);
            p.add(cb.equal(c.get("slug"), s.category()));
        }
        if (s.minPrice() != null) {
            p.add(cb.greaterThanOrEqualTo(root.get("price"), s.minPrice()));
        }
        if (s.maxPrice() != null) {
            p.add(cb.lessThanOrEqualTo(root.get("price"), s.maxPrice()));
        }
        if (s.onlyAvailable()) {
            p.add(cb.greaterThan(root.get("stock"), 0));
        }
        if (s.featured() != null) {
            p.add(cb.equal(root.get("featured"), s.featured()));
        }
        return p.toArray(Predicate[]::new);
    }

    private List<Order> orders(CriteriaBuilder cb, Root<Product> root, String sort) {
        String key = sort == null ? "featured" : sort.toLowerCase(Locale.ROOT);
        List<Order> o = new ArrayList<>();
        switch (key) {
            case "price_asc" -> o.add(cb.asc(root.get("price")));
            case "price_desc" -> o.add(cb.desc(root.get("price")));
            case "newest" -> o.add(cb.desc(root.get("createdAt")));
            case "name" -> o.add(cb.asc(root.get("name")));
            case "stock" -> o.add(cb.asc(root.get("stock")));
            default -> {
                o.add(cb.desc(root.get("featured")));
                o.add(cb.asc(root.get("name")));
            }
        }
        o.add(cb.asc(root.get("id")));
        return o;
    }

    @Transactional(readOnly = true)
    public Product bySlug(String slug, boolean includeInactive) {
        Product p = products.findBySlug(slug).orElseThrow(() -> ApiException.notFound("Produto não encontrado."));
        if (!includeInactive && !p.isActive()) {
            throw ApiException.notFound("Produto não encontrado.");
        }
        if (p.getCategory() != null) {
            p.getCategory().getName();
        }
        return p;
    }

    @Transactional(readOnly = true)
    public Product get(Long id) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Produto não encontrado."));
        if (p.getCategory() != null) {
            p.getCategory().getName();
        }
        return p;
    }

    @Transactional
    public Product save(Long id, CatalogDtos.ProductInput in, Long userId) {
        Product p = id == null ? new Product() : products.findById(id).orElseThrow(() -> ApiException.notFound("Produto não encontrado."));
        if (id != null && in.version() != null && p.getVersion() != null && !in.version().equals(p.getVersion())) {
            throw ApiException.conflict("CONCURRENT_UPDATE", "Outra pessoa alterou este produto. Recarregue e tente de novo.");
        }
        String name = Texts.clean(in.name());
        p.setName(name);
        String sku = Texts.clean(in.sku());
        if (sku == null) {
            sku = id == null ? nextSku(name) : p.getSku();
        }
        sku = sku.toUpperCase(Locale.ROOT);
        if (!sku.equals(p.getSku()) && products.existsBySku(sku)) {
            throw ApiException.conflict("SKU_TAKEN", "Já existe um produto com o código " + sku + ".");
        }
        p.setSku(sku);
        if (p.getSlug() == null || !Texts.slug(name).equals(p.getSlug())) {
            p.setSlug(uniqueSlug(name, p.getId()));
        }
        p.setDescription(Texts.clean(in.description()));
        p.setPrice(Money.of(in.price()));
        p.setCompareAtPrice(in.compareAtPrice() == null || in.compareAtPrice().signum() == 0 ? null : Money.of(in.compareAtPrice()));
        p.setCategory(in.categoryId() == null ? null : categories.findById(in.categoryId())
                .orElseThrow(() -> ApiException.badRequest("CATEGORY", "Categoria não encontrada.")));
        p.setImageUrl(Texts.clean(in.imageUrl()));
        p.setLowStockThreshold(in.lowStockThreshold() == null ? 5 : in.lowStockThreshold());
        p.setWeightGrams(in.weightGrams() == null ? 0 : in.weightGrams());
        p.setActive(in.active() == null || in.active());
        p.setFeatured(in.featured() != null && in.featured());
        boolean creating = p.getId() == null;
        int initial = in.stock() == null ? 0 : in.stock();
        if (creating) {
            p.setStock(initial);
        }
        Product saved = products.save(p);
        if (creating && initial > 0) {
            record(saved, MovementType.IN, initial, "Estoque inicial", null, userId);
        }
        return saved;
    }

    @Transactional
    public Product adjustStock(Long id, CatalogDtos.StockInput in, Long userId) {
        Product p = products.lockById(id).orElseThrow(() -> ApiException.notFound("Produto não encontrado."));
        int q = in.quantity();
        int delta = switch (in.type()) {
            case IN -> Math.abs(q);
            case OUT -> -Math.abs(q);
            case ADJUST -> q - p.getStock();
            default -> throw ApiException.badRequest("MOVEMENT", "Use entrada, saída ou ajuste.");
        };
        if (in.type() == MovementType.ADJUST && q < 0) {
            throw ApiException.badRequest("STOCK", "O estoque não pode ser negativo.");
        }
        if (p.getStock() + delta < 0) {
            throw ApiException.badRequest("STOCK", "Estoque insuficiente para essa saída.");
        }
        p.setStock(p.getStock() + delta);
        products.save(p);
        record(p, in.type(), delta, Texts.clean(in.reason()), null, userId);
        return p;
    }

    @Transactional
    public void reserve(Long productId, int quantity, Long orderId, Long userId) {
        Product p = products.lockById(productId).orElseThrow(() -> ApiException.notFound("Produto não encontrado."));
        if (!p.isActive()) {
            throw ApiException.conflict("UNAVAILABLE", p.getName() + " não está mais disponível.");
        }
        if (p.getStock() < quantity) {
            throw ApiException.conflict("OUT_OF_STOCK", "Só restam " + p.getStock() + " unidade(s) de " + p.getName() + ".");
        }
        p.setStock(p.getStock() - quantity);
        products.save(p);
        record(p, MovementType.SALE, -quantity, "Venda", orderId, userId);
    }

    @Transactional
    public void restock(Long productId, int quantity, Long orderId, Long userId, String reason) {
        products.lockById(productId).ifPresent(p -> {
            p.setStock(p.getStock() + quantity);
            products.save(p);
            record(p, MovementType.RETURN, quantity, reason, orderId, userId);
        });
    }

    @Transactional
    public boolean delete(Long id) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Produto não encontrado."));
        cartItems.deleteByProduct(id);
        if (orderItems.existsByProductId(id) || movements.existsByProductIdAndTypeNot(id, MovementType.IN)) {
            p.setActive(false);
            products.save(p);
            return false;
        }
        movements.deleteByProductId(id);
        products.delete(p);
        return true;
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.MovementView> movements(Long productId) {
        return movements.findTop50ByProductIdOrderByIdDesc(productId).stream()
                .map(m -> new CatalogDtos.MovementView(m.getId(), m.getType(), m.getQuantity(), m.getStockAfter(), m.getReason(),
                        m.getOrderId(), Times.instant(m.getCreatedAt())))
                .toList();
    }

    @Transactional
    public Category saveCategory(Long id, CatalogDtos.CategoryInput in) {
        Category c = id == null ? new Category() : categories.findById(id).orElseThrow(() -> ApiException.notFound("Categoria não encontrada."));
        String name = Texts.clean(in.name());
        c.setName(name);
        String slug = Texts.slug(name);
        if (!slug.equals(c.getSlug())) {
            String candidate = slug;
            int n = 2;
            while (categories.existsBySlug(candidate)) {
                candidate = slug + "-" + n++;
            }
            c.setSlug(candidate);
        }
        c.setDescription(Texts.clean(in.description()));
        c.setSortOrder(in.sortOrder() == null ? 0 : in.sortOrder());
        c.setActive(in.active() == null || in.active());
        return categories.save(c);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Categoria não encontrada."));
        if (products.countByCategoryId(id) > 0) {
            throw ApiException.conflict("IN_USE", "Mova ou exclua os produtos desta categoria antes de excluí-la.");
        }
        categories.delete(c);
    }

    private void record(Product p, MovementType type, int quantity, String reason, Long orderId, Long userId) {
        InventoryMovement m = new InventoryMovement();
        m.setProductId(p.getId());
        m.setType(type);
        m.setQuantity(quantity);
        m.setStockAfter(p.getStock());
        m.setReason(reason);
        m.setOrderId(orderId);
        m.setUserId(userId);
        m.setCreatedAt(LocalDateTime.now(clock));
        movements.save(m);
    }

    private String uniqueSlug(String name, Long selfId) {
        String base = Texts.slug(name);
        String candidate = base;
        int n = 2;
        while (true) {
            var existing = products.findBySlug(candidate);
            if (existing.isEmpty() || existing.get().getId().equals(selfId)) {
                return candidate;
            }
            candidate = base + "-" + n++;
        }
    }

    private String nextSku(String name) {
        String prefix = Texts.slug(name).replace("-", "").toUpperCase(Locale.ROOT);
        prefix = prefix.length() >= 3 ? prefix.substring(0, 3) : (prefix + "VLO").substring(0, 3);
        int n = 1;
        String sku;
        do {
            sku = String.format("VL-%s-%03d", prefix, n++);
        } while (products.existsBySku(sku));
        return sku;
    }

    public static CatalogDtos.ProductCard card(Product p) {
        Category c = p.getCategory();
        return new CatalogDtos.ProductCard(p.getId(), p.getSku(), p.getName(), p.getSlug(), p.getPrice(), p.getCompareAtPrice(),
                p.getImageUrl(), p.getStock(), p.isFeatured(), c == null ? null : c.getName(), c == null ? null : c.getSlug());
    }

    public static CatalogDtos.ProductDetail detail(Product p) {
        Category c = p.getCategory();
        return new CatalogDtos.ProductDetail(p.getId(), p.getSku(), p.getName(), p.getSlug(), p.getDescription(), p.getPrice(),
                p.getCompareAtPrice(), p.getImageUrl(), p.getStock(), p.getLowStockThreshold(), p.getWeightGrams(), p.isActive(),
                p.isFeatured(), c == null ? null : c.getId(), c == null ? null : c.getName(), c == null ? null : c.getSlug(),
                p.getVersion(), Times.instant(p.getCreatedAt()));
    }
}
