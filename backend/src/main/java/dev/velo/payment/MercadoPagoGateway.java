package dev.velo.payment;

import dev.velo.common.Times;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.time.Instant;
import dev.velo.common.Money;
import dev.velo.config.VeloProperties;
import dev.velo.order.Order;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MercadoPagoGateway implements PaymentGateway {

    private static final DateTimeFormatter EXPIRATION = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx");

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoGateway.class);
    private static final String API = "https://api.mercadopago.com/";

    private final VeloProperties props;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public MercadoPagoGateway(VeloProperties props) {
        this.props = props;
    }

    public boolean configured() {
        return props.payments().mercadoPago().configured();
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MERCADO_PAGO;
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CHECKOUT;
    }

    @Override
    public GatewayResult start(Order order, PaymentRequest request) {
        if (!configured()) {
            return GatewayResult.failed("Mercado Pago não está configurado.");
        }
        String base = props.publicUrl();
        String back = base + "/pedidos/" + order.getNumber();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", "Pedido " + order.getNumber() + " · Velo");
        item.put("quantity", 1);
        item.put("currency_id", "BRL");
        item.put("unit_price", Money.of(order.getTotal()));
        Map<String, Object> backUrls = new LinkedHashMap<>();
        backUrls.put("success", back + "?pagamento=retorno");
        backUrls.put("pending", back + "?pagamento=retorno");
        backUrls.put("failure", back + "?pagamento=cancelado");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item));
        body.put("external_reference", order.getNumber());
        body.put("back_urls", backUrls);
        // O Mercado Pago recusa auto_return e notification_url apontando para endereços locais (http://localhost).
        if (base.startsWith("https://")) {
            body.put("auto_return", "approved");
            body.put("notification_url", base + "/api/payments/webhook/mercadopago");
        }
        body.put("statement_descriptor", "VELO");
        // A preferência vence junto com o prazo de pagamento do pedido, para PIX e boleto não serem pagos depois do cancelamento.
        Instant deadline = Times.instant(order.getPlacedAt()).plus(Duration.ofMinutes(props.orders().paymentWindowMinutes()));
        Instant floor = Instant.now().plus(Duration.ofMinutes(5));
        String until = EXPIRATION.format((deadline.isAfter(floor) ? deadline : floor).atOffset(ZoneOffset.UTC));
        body.put("expires", true);
        body.put("expiration_date_to", until);
        body.put("date_of_expiration", until);
        try {
            JsonNode res = post("checkout/preferences", body, "velo-" + order.getNumber() + "-" + System.currentTimeMillis() / 60000);
            String id = res.path("id").asString("");
            String url = res.path("init_point").asString("");
            if (id.isEmpty() || url.isEmpty()) {
                throw new MercadoPagoException("resposta sem id/init_point");
            }
            return new GatewayResult(PaymentStatus.PENDING, id, url, null, null, null, null, null, null);
        } catch (MercadoPagoException e) {
            log.warn("Mercado Pago checkout failed: {}", e.getMessage());
            return GatewayResult.failed("Não foi possível iniciar o pagamento no Mercado Pago.");
        }
    }

    @Override
    public boolean refund(Payment payment, BigDecimal amount) {
        if (!configured() || payment.getExternalId() == null || !safeId(payment.getExternalId())) {
            return false;
        }
        try {
            post("v1/payments/" + payment.getExternalId() + "/refunds", Map.of("amount", Money.of(amount)),
                    "velo-refund-" + payment.getId());
            return true;
        } catch (MercadoPagoException e) {
            log.warn("Mercado Pago refund failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Consulta um pagamento no Mercado Pago (usado pelo webhook). Lança {@link MercadoPagoException} em caso de falha.
     */
    JsonNode payment(String id) {
        if (!configured() || !safeId(id)) {
            throw new MercadoPagoException("pagamento inválido ou gateway não configurado");
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + "v1/payments/" + id))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + props.payments().mercadoPago().accessToken())
                .GET()
                .build();
        return send(req);
    }

    static boolean safeId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,64}");
    }

    private JsonNode post(String path, Object body, String idempotencyKey) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + props.payments().mercadoPago().accessToken())
                .header("Content-Type", "application/json")
                .header("X-Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        return send(req);
    }

    private JsonNode send(HttpRequest req) {
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            String text = res.body() == null || res.body().isBlank() ? "{}" : res.body();
            JsonNode node;
            try {
                node = json.readTree(text);
            } catch (RuntimeException e) {
                throw new MercadoPagoException(res.statusCode() + " resposta inválida");
            }
            if (res.statusCode() >= 300) {
                throw new MercadoPagoException(res.statusCode() + " " + node.path("message").asString(""));
            }
            return node;
        } catch (IOException e) {
            throw new MercadoPagoException(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MercadoPagoException("interrupted");
        }
    }

    static final class MercadoPagoException extends RuntimeException {
        MercadoPagoException(String message) {
            super(message);
        }
    }
}
