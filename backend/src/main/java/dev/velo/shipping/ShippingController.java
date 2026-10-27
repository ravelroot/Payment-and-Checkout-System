package dev.velo.shipping;

import dev.velo.common.Money;
import dev.velo.order.Pricing;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShippingController {

    public record Quote(String cep, String region, String state, BigDecimal cost, boolean free, BigDecimal freeAbove, int minDays,
                        int maxDays) {
    }

    private final CepService ceps;
    private final Pricing pricing;

    public ShippingController(CepService ceps, Pricing pricing) {
        this.ceps = ceps;
        this.pricing = pricing;
    }

    @GetMapping("/api/address/cep/{cep}")
    public CepAddress cep(@PathVariable String cep) {
        return ceps.find(cep);
    }

    @GetMapping("/api/shipping/quote")
    public Quote quote(@RequestParam(required = false) String cep, @RequestParam(required = false) BigDecimal subtotal) {
        String digits = CepService.digits(cep);
        BigDecimal value = subtotal == null || subtotal.signum() < 0 ? Money.ZERO : Money.of(subtotal);
        BigDecimal cost = pricing.shipping(value, 1);
        ShippingQuotes.Destination d = ShippingQuotes.destination(digits);
        return new Quote(CepService.format(digits), d.region(), d.state(), cost, cost.signum() == 0, pricing.freeShippingAbove(),
                d.minDays(), d.maxDays());
    }
}
