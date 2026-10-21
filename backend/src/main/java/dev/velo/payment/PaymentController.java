package dev.velo.payment;

import dev.velo.auth.CurrentUser;
import dev.velo.auth.VeloPrincipal;
import dev.velo.order.OrderDtos;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping("/options")
    public PaymentService.Options options() {
        return payments.options();
    }

    @PostMapping("/{id}/simulate")
    public OrderDtos.PaymentView simulate(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        VeloPrincipal p = CurrentUser.get();
        boolean approve = Boolean.TRUE.equals(body.get("approve"));
        return PaymentService.view(payments.simulate(id, approve, p.userId(), p.role().isBackoffice()));
    }
}
