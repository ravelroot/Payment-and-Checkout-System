package dev.velo.coupon;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/coupons")
public class AdminCouponController {

    private final CouponService coupons;

    public AdminCouponController(CouponService coupons) {
        this.coupons = coupons;
    }

    @GetMapping
    public List<CouponDtos.View> list() {
        return coupons.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponDtos.View create(@Valid @RequestBody CouponDtos.Input in) {
        return coupons.save(null, in);
    }

    @PutMapping("/{id}")
    public CouponDtos.View update(@PathVariable Long id, @Valid @RequestBody CouponDtos.Input in) {
        return coupons.save(id, in);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        coupons.delete(id);
        return ResponseEntity.noContent().build();
    }
}
