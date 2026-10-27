package dev.velo.payment;

import dev.velo.config.VeloProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class MercadoPagoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);
    // O status é sempre consultado de novo na API do Mercado Pago, então repetir um aviso antigo não muda nada; a
    // janela larga evita recusar as novas tentativas que o Mercado Pago faz por horas quando a consulta falha.
    private static final long TOLERANCE_SECONDS = 48 * 3600;

    private final PaymentService payments;
    private final MercadoPagoGateway gateway;
    private final VeloProperties props;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    public MercadoPagoWebhookController(PaymentService payments, MercadoPagoGateway gateway, VeloProperties props, Clock clock) {
        this.payments = payments;
        this.gateway = gateway;
        this.props = props;
        this.clock = clock;
    }

    @PostMapping("/api/payments/webhook/mercadopago")
    public ResponseEntity<Void> handle(@RequestBody(required = false) String payload,
                                       @RequestParam(name = "data.id", required = false) String queryDataId,
                                       @RequestParam(name = "type", required = false) String queryType,
                                       @RequestParam(name = "topic", required = false) String queryTopic,
                                       @RequestHeader(name = "x-signature", required = false) String signature,
                                       @RequestHeader(name = "x-request-id", required = false) String requestId) {
        JsonNode body = parse(payload);
        String dataId = queryDataId != null && !queryDataId.isBlank() ? queryDataId : body.path("data").path("id").asString("");
        String secret = props.payments().mercadoPago().webhookSecret();
        if (secret == null || secret.isBlank()
                || !verify(dataId, requestId, signature, secret, Instant.now(clock).getEpochSecond())) {
            return ResponseEntity.badRequest().build();
        }
        String type = body.path("type").asString("");
        if (type.isEmpty()) {
            type = queryType != null ? queryType : queryTopic == null ? "" : queryTopic;
        }
        if (!"payment".equals(type) || dataId.isBlank()) {
            log.debug("Ignoring Mercado Pago notification {}", type);
            return ResponseEntity.ok().build();
        }
        JsonNode mp;
        try {
            mp = gateway.payment(dataId);
        } catch (MercadoPagoGateway.MercadoPagoException e) {
            log.warn("Mercado Pago payment lookup failed for {}: {}", dataId, e.getMessage());
            // Responde erro para o Mercado Pago reenviar a notificação depois.
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
        String status = mp.path("status").asString("");
        String reference = mp.path("external_reference").asString("");
        String paymentId = mp.path("id").asString(dataId);
        if (!reference.isEmpty()) {
            payments.mercadoPagoNotified(reference, paymentId, status);
        }
        return ResponseEntity.ok().build();
    }

    private JsonNode parse(String payload) {
        try {
            return json.readTree(payload == null || payload.isBlank() ? "{}" : payload);
        } catch (RuntimeException e) {
            return json.readTree("{}");
        }
    }

    /**
     * Valida o cabeçalho {@code x-signature} ("ts=...,v1=...") do Mercado Pago: HMAC-SHA256 em hexadecimal sobre o manifesto
     * {@code id:{data.id};request-id:{x-request-id};ts:{ts};}. Partes ausentes saem do manifesto, como manda a documentação.
     */
    static boolean verify(String dataId, String requestId, String header, String secret, long nowSeconds) {
        if (header == null || header.isBlank() || secret == null || secret.isBlank()) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals("ts")) {
                ts = kv[1].trim();
            } else if (kv.length == 2 && kv[0].trim().equals("v1")) {
                v1 = kv[1].trim();
            }
        }
        if (ts == null || v1 == null || ts.isEmpty() || v1.isEmpty()) {
            return false;
        }
        try {
            long t = Long.parseLong(ts);
            long seconds = t >= 100_000_000_000L ? t / 1000 : t;
            if (Math.abs(nowSeconds - seconds) > TOLERANCE_SECONDS) {
                return false;
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(manifest(dataId, requestId, ts).getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expected, HexFormat.of().parseHex(v1));
        } catch (Exception e) {
            return false;
        }
    }

    static String manifest(String dataId, String requestId, String ts) {
        StringBuilder m = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            String id = dataId.trim();
            m.append("id:").append(id.matches("[A-Za-z0-9]+") ? id.toLowerCase(Locale.ROOT) : id).append(';');
        }
        if (requestId != null && !requestId.isBlank()) {
            m.append("request-id:").append(requestId.trim()).append(';');
        }
        m.append("ts:").append(ts).append(';');
        return m.toString();
    }
}
