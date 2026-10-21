package dev.velo.payment;

import dev.velo.config.VeloProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);
    private static final long TOLERANCE_SECONDS = 300;

    private final PaymentService payments;
    private final VeloProperties props;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    public StripeWebhookController(PaymentService payments, VeloProperties props, Clock clock) {
        this.payments = payments;
        this.props = props;
        this.clock = clock;
    }

    @PostMapping("/api/payments/webhook/stripe")
    public ResponseEntity<Void> handle(@RequestBody String payload,
                                       @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        String secret = props.payments().stripe().webhookSecret();
        if (secret == null || secret.isBlank() || !verify(payload, signature, secret, Instant.now(clock).getEpochSecond())) {
            return ResponseEntity.badRequest().build();
        }
        JsonNode event = json.readTree(payload);
        String type = event.path("type").asString("");
        JsonNode obj = event.path("data").path("object");
        String sessionId = obj.path("id").asString("");
        switch (type) {
            case "checkout.session.completed" -> payments.stripeCompleted(sessionId, "paid".equals(obj.path("payment_status").asString("")));
            case "checkout.session.async_payment_succeeded" -> payments.stripeCompleted(sessionId, true);
            case "checkout.session.async_payment_failed", "checkout.session.expired" -> payments.stripeCompleted(sessionId, false);
            default -> log.debug("Ignoring Stripe event {}", type);
        }
        return ResponseEntity.ok().build();
    }

    static boolean verify(String payload, String header, String secret, long now) {
        if (header == null) {
            return false;
        }
        String timestamp = null;
        java.util.List<String> signatures = new java.util.ArrayList<>();
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals("t")) {
                timestamp = kv[1];
            } else if (kv.length == 2 && kv[0].equals("v1")) {
                signatures.add(kv[1]);
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            return false;
        }
        try {
            long t = Long.parseLong(timestamp);
            if (Math.abs(now - t) > TOLERANCE_SECONDS) {
                return false;
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            for (String s : signatures) {
                if (MessageDigest.isEqual(expected, HexFormat.of().parseHex(s))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
