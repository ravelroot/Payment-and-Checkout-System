package dev.velo.payment;

import dev.velo.common.Money;
import dev.velo.config.VeloProperties;
import dev.velo.order.Order;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class StripeGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripeGateway.class);
    private static final String API = "https://api.stripe.com/v1/";

    private final VeloProperties props;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public StripeGateway(VeloProperties props) {
        this.props = props;
    }

    public boolean configured() {
        return props.payments().stripe().configured();
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CHECKOUT;
    }

    @Override
    public GatewayResult start(Order order, PaymentRequest request) {
        if (!configured()) {
            return GatewayResult.failed("Stripe não está configurado.");
        }
        String base = props.publicUrl();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("mode", "payment");
        form.put("success_url", base + "/pedidos/" + order.getNumber() + "?pagamento=retorno");
        form.put("cancel_url", base + "/pedidos/" + order.getNumber() + "?pagamento=cancelado");
        form.put("client_reference_id", order.getNumber());
        form.put("metadata[order_number]", order.getNumber());
        form.put("line_items[0][quantity]", "1");
        form.put("line_items[0][price_data][currency]", "brl");
        form.put("line_items[0][price_data][unit_amount]", String.valueOf(Money.cents(order.getTotal())));
        form.put("line_items[0][price_data][product_data][name]", "Pedido " + order.getNumber() + " · Velo");
        try {
            JsonNode body = post("checkout/sessions", form, "velo-" + order.getNumber() + "-" + System.currentTimeMillis() / 60000);
            return new GatewayResult(PaymentStatus.PENDING, body.path("id").asString(), body.path("url").asString(), null, null,
                    null, null, null, null);
        } catch (StripeException e) {
            log.warn("Stripe checkout failed: {}", e.getMessage());
            return GatewayResult.failed("Não foi possível iniciar o pagamento no Stripe.");
        }
    }

    @Override
    public boolean refund(Payment payment, BigDecimal amount) {
        if (!configured() || payment.getExternalId() == null) {
            return false;
        }
        try {
            JsonNode session = get("checkout/sessions/" + payment.getExternalId());
            String intent = session.path("payment_intent").asString(null);
            if (intent == null) {
                return false;
            }
            Map<String, String> form = new LinkedHashMap<>();
            form.put("payment_intent", intent);
            form.put("amount", String.valueOf(Money.cents(amount)));
            post("refunds", form, "velo-refund-" + payment.getId());
            return true;
        } catch (StripeException e) {
            log.warn("Stripe refund failed: {}", e.getMessage());
            return false;
        }
    }

    private JsonNode post(String path, Map<String, String> form, String idempotencyKey) {
        String body = form.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + props.payments().stripe().secretKey())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return send(req);
    }

    private JsonNode get(String path) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + props.payments().stripe().secretKey())
                .GET()
                .build();
        return send(req);
    }

    private JsonNode send(HttpRequest req) {
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode node = json.readTree(res.body());
            if (res.statusCode() >= 300) {
                throw new StripeException(res.statusCode() + " " + node.path("error").path("message").asString(""));
            }
            return node;
        } catch (IOException e) {
            throw new StripeException(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StripeException("interrupted");
        }
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    static final class StripeException extends RuntimeException {
        StripeException(String message) {
            super(message);
        }
    }
}
