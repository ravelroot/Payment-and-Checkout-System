package dev.velo.integration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import dev.velo.common.ApiException;
import dev.velo.config.VeloProperties;
import dev.velo.order.OrderEvent;
import dev.velo.order.OrderItemRepository;
import dev.velo.user.User;
import dev.velo.user.UserRepository;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Webhooks de saída: avisa um sistema externo (VELO_WEBHOOK_URL) sobre eventos de pedidos. Os eventos só saem depois
 * que a transação é confirmada e são enviados em segundo plano, sem nunca travar ou desfazer a operação de negócio.
 */
@Component
public class WebhookService implements DisposableBean {

    public static final String TEST_EVENT = "test.ping";
    public static final List<String> EVENTS = List.of(OrderEvent.CREATED, "order.paid", "order.processing", "order.shipped",
            "order.delivered", "order.canceled", "order.refunded", TEST_EVENT);
    public static final String USER_AGENT = "Velo-Webhooks/1.15";
    static final int MAX_ATTEMPTS = 3;
    static final int HISTORY = 50;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final long[] BACKOFF_MILLIS = {1000, 3000};

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    public record Delivery(String id, String type, String url, String status, Integer httpStatus, int attempts, long durationMs,
                           Instant at, String error) {
    }

    private final VeloProperties props;
    private final OrderItemRepository items;
    private final UserRepository users;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final JsonMapper json = JsonMapper.builder().build();
    // Poucos envios ao mesmo tempo e fila limitada: uma rajada de eventos não disputa o banco com as requisições.
    private final ExecutorService executor = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1000),
            Thread.ofVirtual().name("velo-webhook-", 0).factory());
    private final SecureRandom random = new SecureRandom();
    private final Deque<Delivery> history = new ArrayDeque<>();

    public WebhookService(VeloProperties props, OrderItemRepository items, UserRepository users, Clock clock) {
        this.props = props;
        this.items = items;
        this.users = users;
        this.clock = clock;
    }

    public boolean configured() {
        return props.webhooks().configured();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderEvent(OrderEvent event) {
        if (!configured()) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    deliver(event.type(), orderData(event), event.at());
                } catch (RuntimeException e) {
                    log.warn("Webhook {} for order {} failed: {}", event.type(), event.number(), e.getMessage());
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("Webhook {} for order {} not scheduled: {}", event.type(), event.number(), e.getMessage());
        }
    }

    /**
     * Envia um {@code test.ping} na hora e devolve o registro da entrega.
     */
    public Delivery sendTest() {
        if (!configured()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEBHOOK_NOT_CONFIGURED", "Defina VELO_WEBHOOK_URL para enviar eventos.");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "Evento de teste enviado pelo painel do Velo.");
        return deliver(TEST_EVENT, data, Instant.now(clock));
    }

    /**
     * Últimas entregas, da mais recente para a mais antiga.
     */
    public List<Delivery> deliveries() {
        synchronized (history) {
            return List.copyOf(history);
        }
    }

    Delivery deliver(String type, Map<String, Object> data, Instant createdAt) {
        String id = "evt_" + randomHex(12);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("type", type);
        payload.put("createdAt", createdAt.toString());
        payload.put("data", data);
        String body = json.writeValueAsString(payload);
        String url = props.webhooks().url();
        String secret = props.webhooks().secret();
        Instant at = Instant.now(clock);
        long started = System.nanoTime();

        URI uri = target(url);
        if (uri == null) {
            return record(new Delivery(id, type, url, "FAILED", null, 0, 0, at, "URL de webhook inválida."));
        }
        int attempts = 0;
        Integer httpStatus = null;
        String error = null;
        boolean ok = false;
        while (attempts < MAX_ATTEMPTS) {
            if (attempts > 0 && !pause(BACKOFF_MILLIS[attempts - 1])) {
                error = "Envio interrompido.";
                break;
            }
            attempts++;
            HttpRequest.Builder req = HttpRequest.newBuilder(uri)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .header("X-Velo-Event", type)
                    .header("X-Velo-Delivery", id)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (secret != null) {
                req.header("X-Velo-Signature", signature(secret, Instant.now(clock).getEpochSecond(), body));
            }
            try {
                HttpResponse<Void> res = http.send(req.build(), HttpResponse.BodyHandlers.discarding());
                httpStatus = res.statusCode();
                if (httpStatus >= 200 && httpStatus < 300) {
                    ok = true;
                    error = null;
                    break;
                }
                error = "O destino respondeu HTTP " + httpStatus + ".";
                if (httpStatus < 500) {
                    break;
                }
            } catch (HttpTimeoutException e) {
                httpStatus = null;
                error = "Tempo de resposta esgotado (5 s).";
            } catch (ConnectException e) {
                httpStatus = null;
                error = "Não foi possível conectar ao destino.";
            } catch (IOException e) {
                httpStatus = null;
                error = "Falha de rede: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                error = "Envio interrompido.";
                break;
            }
        }
        long duration = (System.nanoTime() - started) / 1_000_000;
        Delivery d = new Delivery(id, type, url, ok ? "SUCCESS" : "FAILED", httpStatus, attempts, duration, at, error);
        if (!ok) {
            log.warn("Webhook {} ({}) failed after {} attempt(s): {}", type, id, attempts, error);
        }
        return record(d);
    }

    private Map<String, Object> orderData(OrderEvent e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("number", e.number());
        data.put("status", e.status() == null ? null : e.status().name());
        data.put("total", e.total());
        data.put("itemsCount", itemsCount(e.orderId()));
        data.put("trackingCode", e.trackingCode());
        data.put("customerEmail", customerEmail(e.userId()));
        return data;
    }

    private Integer itemsCount(Long orderId) {
        if (orderId == null) {
            return null;
        }
        try {
            int total = 0;
            for (Object[] row : items.quantities(List.of(orderId))) {
                total += ((Number) row[1]).intValue();
            }
            return total;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String customerEmail(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            return users.findById(userId).map(User::getEmail).orElse(null);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Delivery record(Delivery d) {
        synchronized (history) {
            history.addFirst(d);
            while (history.size() > HISTORY) {
                history.removeLast();
            }
        }
        return d;
    }

    private static URI target(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null ? uri : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean pause(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    /**
     * Valor do cabeçalho {@code X-Velo-Signature}: {@code t=<epoch s>,v1=<hex HMAC-SHA256 de "<t>.<corpo>">}.
     */
    public static String signature(String secret, long timestamp, String body) {
        return "t=" + timestamp + ",v1=" + hmacHex(secret, timestamp + "." + body);
    }

    static String hmacHex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível", e);
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
