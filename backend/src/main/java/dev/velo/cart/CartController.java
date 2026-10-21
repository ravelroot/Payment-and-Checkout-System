package dev.velo.cart;

import dev.velo.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService carts;

    public CartController(CartService carts) {
        this.carts = carts;
    }

    @GetMapping
    public CartDtos.View get(@RequestParam(required = false) String coupon) {
        return carts.view(CurrentUser.id(), coupon);
    }

    @PostMapping("/items")
    public CartDtos.View add(@Valid @RequestBody CartDtos.AddItem in, @RequestParam(required = false) String coupon) {
        carts.add(CurrentUser.id(), in.productId(), in.quantity() == null ? 1 : in.quantity());
        return carts.view(CurrentUser.id(), coupon);
    }

    @PutMapping("/items/{productId}")
    public CartDtos.View set(@PathVariable Long productId, @Valid @RequestBody CartDtos.SetQuantity in,
                             @RequestParam(required = false) String coupon) {
        carts.set(CurrentUser.id(), productId, in.quantity());
        return carts.view(CurrentUser.id(), coupon);
    }

    @DeleteMapping("/items/{productId}")
    public CartDtos.View remove(@PathVariable Long productId, @RequestParam(required = false) String coupon) {
        carts.remove(CurrentUser.id(), productId);
        return carts.view(CurrentUser.id(), coupon);
    }

    @DeleteMapping
    public CartDtos.View clear() {
        carts.clear(CurrentUser.id());
        return carts.view(CurrentUser.id(), null);
    }
}
