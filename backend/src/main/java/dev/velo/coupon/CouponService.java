package dev.velo.coupon;

import dev.velo.common.ApiException;
import dev.velo.common.Money;
import dev.velo.common.Times;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {

    public record Applied(Coupon coupon, BigDecimal discount, String message) {
    }

    private final CouponRepository coupons;
    private final Clock clock;

    public CouponService(CouponRepository coupons, Clock clock) {
        this.coupons = coupons;
        this.clock = clock;
    }

    public static String normalize(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public Optional<Applied> evaluate(String code, BigDecimal subtotal) {
        String c = normalize(code);
        if (c == null) {
            return Optional.empty();
        }
        Coupon coupon = coupons.findByCode(c).orElse(null);
        if (coupon == null) {
            return Optional.of(new Applied(null, Money.ZERO, "Cupom não encontrado."));
        }
        String problem = problem(coupon, subtotal);
        if (problem != null) {
            return Optional.of(new Applied(null, Money.ZERO, problem));
        }
        BigDecimal discount = coupon.getType() == DiscountType.PERCENT
                ? subtotal.multiply(coupon.getValue()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : coupon.getValue();
        discount = Money.of(discount.min(subtotal));
        return Optional.of(new Applied(coupon, discount, "Cupom aplicado."));
    }

    private String problem(Coupon c, BigDecimal subtotal) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!c.isActive()) {
            return "Este cupom não está ativo.";
        }
        if (c.getStartsAt() != null && now.isBefore(c.getStartsAt())) {
            return "Este cupom ainda não está valendo.";
        }
        if (c.getEndsAt() != null && now.isAfter(c.getEndsAt())) {
            return "Este cupom expirou.";
        }
        if (c.getMaxUses() != null && c.getUsedCount() >= c.getMaxUses()) {
            return "Este cupom atingiu o limite de usos.";
        }
        if (c.getMinSubtotal() != null && subtotal.compareTo(c.getMinSubtotal()) < 0) {
            return "Válido para compras a partir de R$ " + c.getMinSubtotal().toPlainString().replace('.', ',') + ".";
        }
        return null;
    }

    @Transactional
    public void consume(Coupon coupon) {
        Coupon fresh = coupons.findById(coupon.getId()).orElseThrow();
        if (fresh.getMaxUses() != null && fresh.getUsedCount() >= fresh.getMaxUses()) {
            throw ApiException.conflict("COUPON_EXHAUSTED", "Este cupom atingiu o limite de usos.");
        }
        fresh.setUsedCount(fresh.getUsedCount() + 1);
        coupons.save(fresh);
    }

    @Transactional
    public void release(String code) {
        String c = normalize(code);
        if (c != null) {
            coupons.findByCode(c).ifPresent(coupon -> {
                coupon.setUsedCount(Math.max(0, coupon.getUsedCount() - 1));
                coupons.save(coupon);
            });
        }
    }

    @Transactional(readOnly = true)
    public List<CouponDtos.View> list() {
        return coupons.findAllByOrderByIdDesc().stream().map(CouponService::view).toList();
    }

    @Transactional
    public CouponDtos.View save(Long id, CouponDtos.Input in) {
        Coupon c = id == null ? new Coupon() : coupons.findById(id).orElseThrow(() -> ApiException.notFound("Cupom não encontrado."));
        String code = normalize(in.code());
        if (!code.equals(c.getCode()) && coupons.existsByCode(code)) {
            throw ApiException.conflict("CODE_TAKEN", "Já existe um cupom " + code + ".");
        }
        if (in.type() == DiscountType.PERCENT && in.value().compareTo(new BigDecimal("100")) > 0) {
            throw ApiException.badRequest("PERCENT", "O percentual vai até 100.");
        }
        if (in.startsAt() != null && in.endsAt() != null && in.endsAt().isBefore(in.startsAt())) {
            throw ApiException.badRequest("DATES", "O fim precisa ser depois do início.");
        }
        c.setCode(code);
        c.setDescription(in.description() == null ? null : in.description().strip());
        c.setType(in.type());
        c.setValue(Money.of(in.value()));
        c.setMinSubtotal(in.minSubtotal() == null || in.minSubtotal().signum() == 0 ? null : Money.of(in.minSubtotal()));
        c.setMaxUses(in.maxUses());
        c.setStartsAt(in.startsAt() == null ? null : LocalDateTime.ofInstant(in.startsAt(), ZoneOffset.UTC));
        c.setEndsAt(in.endsAt() == null ? null : LocalDateTime.ofInstant(in.endsAt(), ZoneOffset.UTC));
        c.setActive(in.active() == null || in.active());
        return view(coupons.save(c));
    }

    @Transactional
    public void delete(Long id) {
        Coupon c = coupons.findById(id).orElseThrow(() -> ApiException.notFound("Cupom não encontrado."));
        if (c.getUsedCount() > 0) {
            c.setActive(false);
            coupons.save(c);
        } else {
            coupons.delete(c);
        }
    }

    static CouponDtos.View view(Coupon c) {
        return new CouponDtos.View(c.getId(), c.getCode(), c.getDescription(), c.getType(), c.getValue(), c.getMinSubtotal(),
                c.getMaxUses(), c.getUsedCount(), Times.instant(c.getStartsAt()), Times.instant(c.getEndsAt()), c.isActive());
    }
}
