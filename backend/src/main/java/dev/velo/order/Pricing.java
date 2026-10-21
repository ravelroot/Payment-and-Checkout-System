package dev.velo.order;

import dev.velo.common.Money;
import dev.velo.config.VeloProperties;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class Pricing {

    private final VeloProperties.Shipping shipping;

    public Pricing(VeloProperties props) {
        this.shipping = props.shipping();
    }

    public BigDecimal shipping(BigDecimal subtotal, int items) {
        if (items == 0) {
            return Money.ZERO;
        }
        if (shipping.freeAbove() != null && subtotal.compareTo(shipping.freeAbove()) >= 0) {
            return Money.ZERO;
        }
        return Money.of(shipping.flatRate());
    }

    public BigDecimal freeShippingAbove() {
        return shipping.freeAbove() == null ? null : Money.of(shipping.freeAbove());
    }
}
