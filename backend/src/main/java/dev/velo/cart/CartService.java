package dev.velo.cart;

import dev.velo.catalog.Product;
import dev.velo.catalog.ProductRepository;
import dev.velo.common.ApiException;
import dev.velo.common.Money;
import dev.velo.coupon.CouponService;
import dev.velo.order.Pricing;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    public static final int MAX_PER_ITEM = 99;

    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductRepository products;
    private final CouponService coupons;
    private final Pricing pricing;

    public CartService(CartRepository carts, CartItemRepository items, ProductRepository products, CouponService coupons,
                       Pricing pricing) {
        this.carts = carts;
        this.items = items;
        this.products = products;
        this.coupons = coupons;
        this.pricing = pricing;
    }

    @Transactional
    public Cart cartOf(Long userId) {
        return carts.findByUserId(userId).orElseGet(() -> {
            Cart c = new Cart();
            c.setUserId(userId);
            return carts.save(c);
        });
    }

    @Transactional(readOnly = true)
    public List<CartItem> lines(Long userId) {
        return carts.findByUserId(userId).map(c -> items.findWithProducts(c.getId())).orElse(List.of());
    }

    @Transactional
    public CartDtos.View view(Long userId, String couponCode) {
        Cart cart = cartOf(userId);
        List<CartItem> lines = items.findWithProducts(cart.getId());
        BigDecimal subtotal = Money.ZERO;
        int count = 0;
        List<CartDtos.Line> out = new java.util.ArrayList<>();
        for (CartItem i : lines) {
            Product p = i.getProduct();
            boolean available = p.isActive() && p.getStock() >= i.getQuantity();
            BigDecimal line = Money.of(p.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())));
            out.add(new CartDtos.Line(p.getId(), p.getSlug(), p.getSku(), p.getName(), p.getImageUrl(), p.getPrice(), i.getQuantity(),
                    line, p.getStock(), available));
            subtotal = subtotal.add(line);
            count += i.getQuantity();
        }
        BigDecimal discount = Money.ZERO;
        String applied = null;
        String message = null;
        var coupon = coupons.evaluate(couponCode, subtotal);
        if (coupon.isPresent()) {
            message = coupon.get().message();
            if (coupon.get().coupon() != null) {
                discount = coupon.get().discount();
                applied = coupon.get().coupon().getCode();
            }
        }
        BigDecimal shipping = pricing.shipping(subtotal, count);
        BigDecimal total = Money.of(subtotal.subtract(discount).add(shipping).max(BigDecimal.ZERO));
        return new CartDtos.View(out, count, Money.of(subtotal), shipping, discount, total, applied, message,
                pricing.freeShippingAbove());
    }

    @Transactional
    public void add(Long userId, Long productId, int quantity) {
        Product p = products.findById(productId).filter(Product::isActive)
                .orElseThrow(() -> ApiException.notFound("Produto não encontrado."));
        Cart cart = cartOf(userId);
        CartItem line = items.findLine(cart.getId(), productId).orElse(null);
        int next = (line == null ? 0 : line.getQuantity()) + Math.max(1, quantity);
        check(p, next);
        if (line == null) {
            line = new CartItem();
            line.setCartId(cart.getId());
            line.setProduct(p);
        }
        line.setQuantity(next);
        items.save(line);
        cart.touch();
        carts.save(cart);
    }

    @Transactional
    public void set(Long userId, Long productId, int quantity) {
        Cart cart = cartOf(userId);
        CartItem line = items.findLine(cart.getId(), productId).orElseThrow(() -> ApiException.notFound("Item não está no carrinho."));
        if (quantity <= 0) {
            items.delete(line);
            return;
        }
        check(line.getProduct(), quantity);
        line.setQuantity(quantity);
        items.save(line);
    }

    @Transactional
    public void remove(Long userId, Long productId) {
        Cart cart = cartOf(userId);
        items.findLine(cart.getId(), productId).ifPresent(items::delete);
    }

    @Transactional
    public void clear(Long userId) {
        carts.findByUserId(userId).ifPresent(c -> items.clear(c.getId()));
    }

    private static void check(Product p, int quantity) {
        if (quantity > MAX_PER_ITEM) {
            throw ApiException.badRequest("QUANTITY", "Máximo de " + MAX_PER_ITEM + " unidades por produto.");
        }
        if (quantity > p.getStock()) {
            throw ApiException.conflict("OUT_OF_STOCK", p.getStock() == 0 ? p.getName() + " está esgotado."
                    : "Só temos " + p.getStock() + " unidade(s) de " + p.getName() + ".");
        }
    }
}
