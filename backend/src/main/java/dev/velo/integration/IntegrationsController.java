package dev.velo.integration;

import dev.velo.config.VeloProperties;
import dev.velo.payment.PaymentProvider;
import dev.velo.payment.PaymentService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/integrations")
public class IntegrationsController {

    public record Provider(PaymentProvider id, String name, boolean configured, boolean active, String webhookUrl,
                           boolean webhookSecretConfigured) {
    }

    public record Webhook(boolean configured, String url, boolean signed, List<String> events) {
    }

    public record Cep(String provider, boolean enabled) {
    }

    public record View(PaymentProvider activeProvider, List<Provider> providers, Webhook webhook,
                       List<WebhookService.Delivery> deliveries, Cep cep) {
    }

    private final PaymentService payments;
    private final WebhookService webhooks;
    private final VeloProperties props;

    public IntegrationsController(PaymentService payments, WebhookService webhooks, VeloProperties props) {
        this.payments = payments;
        this.webhooks = webhooks;
        this.props = props;
    }

    @GetMapping
    public View view() {
        PaymentProvider active = payments.gateway().provider();
        VeloProperties.Payments p = props.payments();
        String base = props.publicUrl();
        List<Provider> providers = List.of(
                new Provider(PaymentProvider.SIMULATED, PaymentService.providerName(PaymentProvider.SIMULATED), true,
                        active == PaymentProvider.SIMULATED, null, false),
                new Provider(PaymentProvider.STRIPE, PaymentService.providerName(PaymentProvider.STRIPE), p.stripe().configured(),
                        active == PaymentProvider.STRIPE, base + "/api/payments/webhook/stripe", p.stripe().webhookSecretConfigured()),
                new Provider(PaymentProvider.MERCADO_PAGO, PaymentService.providerName(PaymentProvider.MERCADO_PAGO),
                        p.mercadoPago().configured(), active == PaymentProvider.MERCADO_PAGO,
                        base + "/api/payments/webhook/mercadopago", p.mercadoPago().webhookSecretConfigured()));
        VeloProperties.Webhooks w = props.webhooks();
        return new View(active, providers, new Webhook(w.configured(), w.url(), w.signed(), WebhookService.EVENTS),
                webhooks.deliveries(), new Cep("ViaCEP", true));
    }

    @PostMapping("/webhook/test")
    public WebhookService.Delivery test() {
        return webhooks.sendTest();
    }
}
