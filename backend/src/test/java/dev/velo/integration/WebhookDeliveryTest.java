package dev.velo.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dev.velo.common.ApiException;
import dev.velo.config.VeloProperties;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebhookDeliveryTest {

    HttpServer server;
    final List<Integer> replies = new CopyOnWriteArrayList<>();
    final AtomicInteger calls = new AtomicInteger();
    final Map<String, String> lastHeaders = new ConcurrentHashMap<>();
    volatile String lastBody;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            int n = calls.getAndIncrement();
            try (InputStream in = exchange.getRequestBody()) {
                lastBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            for (String name : List.of("Content-Type", "User-Agent", "X-Velo-Event", "X-Velo-Delivery", "X-Velo-Signature")) {
                String v = exchange.getRequestHeaders().getFirst(name);
                if (v != null) {
                    lastHeaders.put(name, v);
                }
            }
            int status = n < replies.size() ? replies.get(n) : 204;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    WebhookService service(String url, String secret) {
        VeloProperties props = new VeloProperties(null, null, null, null, new VeloProperties.Webhooks(url, secret), null, false);
        return new WebhookService(props, null, null, Clock.systemUTC());
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
    }

    @Test
    void signedPingIsDelivered() {
        WebhookService webhooks = service(url(), "whsec_velo");
        WebhookService.Delivery d = webhooks.sendTest();
        assertThat(d.status()).isEqualTo("SUCCESS");
        assertThat(d.httpStatus()).isEqualTo(204);
        assertThat(d.attempts()).isEqualTo(1);
        assertThat(d.type()).isEqualTo("test.ping");
        assertThat(d.id()).matches("evt_[0-9a-f]{24}");
        assertThat(d.url()).isEqualTo(url());
        assertThat(d.error()).isNull();
        assertThat(lastHeaders.get("Content-Type")).startsWith("application/json");
        assertThat(lastHeaders.get("User-Agent")).isEqualTo("Velo-Webhooks/1.15");
        assertThat(lastHeaders.get("X-Velo-Event")).isEqualTo("test.ping");
        assertThat(lastHeaders.get("X-Velo-Delivery")).isEqualTo(d.id());
        assertThat(lastBody).contains("\"id\":\"" + d.id() + "\"").contains("\"type\":\"test.ping\"").contains("\"createdAt\":")
                .contains("\"data\":");
        String signature = lastHeaders.get("X-Velo-Signature");
        long t = Long.parseLong(signature.substring(2, signature.indexOf(',')));
        assertThat(signature).isEqualTo(WebhookService.signature("whsec_velo", t, lastBody));
        assertThat(webhooks.deliveries()).containsExactly(d);
    }

    @Test
    void serverErrorsAreRetriedAndClientErrorsAreNot() {
        WebhookService webhooks = service(url(), null);
        replies.add(503);
        WebhookService.Delivery retried = webhooks.sendTest();
        assertThat(retried.status()).isEqualTo("SUCCESS");
        assertThat(retried.attempts()).isEqualTo(2);
        assertThat(lastHeaders).doesNotContainKey("X-Velo-Signature");

        replies.clear();
        calls.set(0);
        replies.add(410);
        WebhookService.Delivery rejected = webhooks.sendTest();
        assertThat(rejected.status()).isEqualTo("FAILED");
        assertThat(rejected.httpStatus()).isEqualTo(410);
        assertThat(rejected.attempts()).isEqualTo(1);
        assertThat(rejected.error()).contains("410");
        assertThat(webhooks.deliveries()).containsExactly(rejected, retried);
    }

    @Test
    void pingNeedsAUrl() {
        WebhookService webhooks = service(null, null);
        assertThatThrownBy(webhooks::sendTest).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("WEBHOOK_NOT_CONFIGURED"));
        assertThat(webhooks.deliveries()).isEmpty();
    }
}
