package dev.velo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.velo.seed.DataSeeder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@VeloTest
class HttpApiTest {

    @Value("${local.server.port}")
    int port;

    final HttpClient http = HttpClient.newHttpClient();
    final JsonMapper json = JsonMapper.builder().build();

    HttpResponse<String> call(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    String login(String email) throws Exception {
        HttpResponse<String> r = call("POST", "/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"" + DataSeeder.DEMO_PASSWORD + "\"}");
        assertThat(r.statusCode()).isEqualTo(200);
        JsonNode n = json.readTree(r.body());
        assertThat(n.path("idleMinutes").asInt()).isEqualTo(20);
        assertThat(n.path("maxMinutes").asInt()).isEqualTo(30);
        return n.path("token").asString();
    }

    @Test
    void everyApiRouteRequiresLogin() throws Exception {
        assertThat(call("GET", "/api/catalog/products", null, null).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/api/cart", "token-invalido", null).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/api/health", null, null).statusCode()).isEqualTo(200);
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        HttpResponse<String> r = call("POST", "/api/auth/login", null, "{\"email\":\"cliente@velo.dev\",\"password\":\"errada123\"}");
        assertThat(r.statusCode()).isEqualTo(401);
        assertThat(json.readTree(r.body()).path("code").asString()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void customerBrowsesButCannotOpenAdmin() throws Exception {
        String token = login(DataSeeder.CUSTOMER_EMAIL);
        HttpResponse<String> products = call("GET", "/api/catalog/products?size=5", token, null);
        assertThat(products.statusCode()).isEqualTo(200);
        assertThat(products.headers().firstValue("X-Velo-Idle-Expires-At")).isPresent();
        assertThat(products.headers().firstValue("X-Velo-Expires-At")).isPresent();
        assertThat(json.readTree(products.body()).path("items").size()).isEqualTo(5);
        assertThat(call("GET", "/api/admin/dashboard", token, null).statusCode()).isEqualTo(403);
        assertThat(call("GET", "/api/admin/users", token, null).statusCode()).isEqualTo(403);
    }

    @Test
    void staffSeesOrdersButNotUsers() throws Exception {
        String token = login(DataSeeder.STAFF_EMAIL);
        assertThat(call("GET", "/api/admin/orders", token, null).statusCode()).isEqualTo(200);
        assertThat(call("GET", "/api/admin/users", token, null).statusCode()).isEqualTo(403);
    }

    @Test
    void adminDashboardAndLogout() throws Exception {
        String token = login(DataSeeder.ADMIN_EMAIL);
        HttpResponse<String> d = call("GET", "/api/admin/dashboard?days=30", token, null);
        assertThat(d.statusCode()).isEqualTo(200);
        JsonNode view = json.readTree(d.body());
        assertThat(view.path("sales").size()).isEqualTo(30);
        assertThat(view.path("kpis").path("orders").asLong()).isPositive();
        JsonNode previous = view.path("previous");
        assertThat(previous.has("revenue")).isTrue();
        assertThat(previous.has("orders")).isTrue();
        assertThat(previous.has("averageTicket")).isTrue();
        assertThat(previous.has("newCustomers")).isTrue();
        JsonNode mix = view.path("paymentMix");
        assertThat(mix.isArray()).isTrue();
        assertThat(mix.size()).isPositive();
        assertThat(mix.get(0).has("method")).isTrue();
        assertThat(mix.get(0).path("orders").asLong()).isPositive();
        for (int i = 1; i < mix.size(); i++) {
            assertThat(mix.get(i - 1).path("amount").asDouble()).isGreaterThanOrEqualTo(mix.get(i).path("amount").asDouble());
        }
        assertThat(call("GET", "/api/admin/users?size=50", token, null).statusCode()).isEqualTo(200);
        assertThat(call("POST", "/api/auth/logout", token, null).statusCode()).isEqualTo(204);
        assertThat(call("GET", "/api/auth/me", token, null).statusCode()).isEqualTo(401);
    }

    @Test
    void registerCreatesACustomerSession() throws Exception {
        HttpResponse<String> r = call("POST", "/api/auth/register", null,
                "{\"fullName\":\"Nova Pessoa\",\"email\":\"nova.pessoa@exemplo.com.br\",\"password\":\"Girassol42\"}");
        assertThat(r.statusCode()).isEqualTo(201);
        String token = json.readTree(r.body()).path("token").asString();
        HttpResponse<String> me = call("GET", "/api/auth/me", token, null);
        assertThat(json.readTree(me.body()).path("role").asString()).isEqualTo("CUSTOMER");
    }

    @Test
    void healthShowsVersion() throws Exception {
        HttpResponse<String> r = call("GET", "/api/health", null, null);
        assertThat(json.readTree(r.body()).path("version").asString()).isEqualTo(VeloApplication.VERSION);
    }

    @Test
    void csvExportIsForBackofficeOnly() throws Exception {
        String admin = login(DataSeeder.ADMIN_EMAIL);
        HttpResponse<String> orders = call("GET", "/api/admin/orders/export", admin, null);
        assertThat(orders.statusCode()).isEqualTo(200);
        assertThat(orders.headers().firstValue("Content-Type").orElse("")).startsWith("text/csv");
        assertThat(orders.headers().firstValue("Content-Disposition").orElse(""))
                .startsWith("attachment").contains("filename=\"velo-pedidos-").endsWith(".csv\"");
        assertThat(orders.body()).startsWith("\uFEFFNúmero;Data;Cliente;E-mail;Situação;Itens;Subtotal;Desconto;Frete;Total;"
                + "Pagamento;Rastreio\r\n");
        assertThat(orders.body().lines().count()).isGreaterThan(1);

        HttpResponse<String> filtered = call("GET", "/api/admin/orders/export?status=DELIVERED&from=2020-01-01", admin, null);
        assertThat(filtered.statusCode()).isEqualTo(200);
        assertThat(filtered.body().lines().skip(1).allMatch(l -> l.contains(";Entregue;"))).isTrue();

        HttpResponse<String> products = call("GET", "/api/admin/products/export", admin, null);
        assertThat(products.statusCode()).isEqualTo(200);
        assertThat(products.headers().firstValue("Content-Type").orElse("")).startsWith("text/csv");
        assertThat(products.body()).startsWith("\uFEFFSKU;Produto;Categoria;Preço;Preço anterior;Estoque;Ativo\r\n");

        String staff = login(DataSeeder.STAFF_EMAIL);
        assertThat(call("GET", "/api/admin/orders/export", staff, null).statusCode()).isEqualTo(200);
        String customer = login(DataSeeder.CUSTOMER_EMAIL);
        assertThat(call("GET", "/api/admin/orders/export", customer, null).statusCode()).isEqualTo(403);
        assertThat(call("GET", "/api/admin/products/export", customer, null).statusCode()).isEqualTo(403);
    }

    @Test
    void integrationsAreForAdminsOnly() throws Exception {
        String admin = login(DataSeeder.ADMIN_EMAIL);
        HttpResponse<String> r = call("GET", "/api/admin/integrations", admin, null);
        assertThat(r.statusCode()).isEqualTo(200);
        JsonNode view = json.readTree(r.body());
        assertThat(view.path("activeProvider").asString()).isEqualTo("SIMULATED");
        JsonNode providers = view.path("providers");
        assertThat(providers.size()).isEqualTo(3);
        assertThat(providers.get(0).path("id").asString()).isEqualTo("SIMULATED");
        assertThat(providers.get(0).path("active").asBoolean()).isTrue();
        assertThat(providers.get(1).path("id").asString()).isEqualTo("STRIPE");
        assertThat(providers.get(1).path("webhookUrl").asString()).endsWith("/api/payments/webhook/stripe");
        assertThat(providers.get(2).path("id").asString()).isEqualTo("MERCADO_PAGO");
        assertThat(providers.get(2).path("name").asString()).isEqualTo("Mercado Pago");
        assertThat(providers.get(2).path("configured").asBoolean()).isFalse();
        assertThat(providers.get(2).path("webhookUrl").asString()).endsWith("/api/payments/webhook/mercadopago");
        assertThat(providers.get(2).has("webhookSecretConfigured")).isTrue();
        assertThat(view.path("webhook").path("configured").asBoolean()).isFalse();
        assertThat(view.path("webhook").path("url").isNull()).isTrue();
        assertThat(view.path("webhook").path("events").size()).isEqualTo(8);
        assertThat(view.path("deliveries").isArray()).isTrue();
        assertThat(view.path("cep").path("provider").asString()).isEqualTo("ViaCEP");

        HttpResponse<String> ping = call("POST", "/api/admin/integrations/webhook/test", admin, null);
        assertThat(ping.statusCode()).isEqualTo(400);
        assertThat(json.readTree(ping.body()).path("code").asString()).isEqualTo("WEBHOOK_NOT_CONFIGURED");

        String staff = login(DataSeeder.STAFF_EMAIL);
        assertThat(call("GET", "/api/admin/integrations", staff, null).statusCode()).isEqualTo(403);
        assertThat(call("POST", "/api/admin/integrations/webhook/test", staff, null).statusCode()).isEqualTo(403);
    }

    @Test
    void mercadoPagoWebhookRejectsUnsignedCalls() throws Exception {
        HttpResponse<String> r = call("POST", "/api/payments/webhook/mercadopago?data.id=123&type=payment", null,
                "{\"type\":\"payment\",\"data\":{\"id\":\"123\"}}");
        assertThat(r.statusCode()).isEqualTo(400);
    }

    @Test
    void shippingQuoteByRegion() throws Exception {
        String token = login(DataSeeder.CUSTOMER_EMAIL);
        JsonNode rio = json.readTree(call("GET", "/api/shipping/quote?cep=22240-003&subtotal=100", token, null).body());
        assertThat(rio.path("cep").asString()).isEqualTo("22240-003");
        assertThat(rio.path("region").asString()).isEqualTo("Sudeste");
        assertThat(rio.path("state").asString()).isEqualTo("RJ");
        assertThat(rio.path("cost").asDouble()).isEqualTo(19.90);
        assertThat(rio.path("free").asBoolean()).isFalse();
        assertThat(rio.path("freeAbove").asDouble()).isEqualTo(299.00);
        assertThat(rio.path("minDays").asInt()).isEqualTo(2);
        assertThat(rio.path("maxDays").asInt()).isEqualTo(4);

        JsonNode free = json.readTree(call("GET", "/api/shipping/quote?cep=90010000&subtotal=299.00", token, null).body());
        assertThat(free.path("region").asString()).isEqualTo("Sul");
        assertThat(free.path("cost").asDouble()).isZero();
        assertThat(free.path("free").asBoolean()).isTrue();

        JsonNode north = json.readTree(call("GET", "/api/shipping/quote?cep=69900000&subtotal=10", token, null).body());
        assertThat(north.path("region").asString()).isEqualTo("Norte");
        assertThat(north.path("state").asString()).isEqualTo("AC");
        assertThat(north.path("maxDays").asInt()).isEqualTo(12);

        HttpResponse<String> invalid = call("GET", "/api/shipping/quote?cep=123&subtotal=10", token, null);
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(json.readTree(invalid.body()).path("code").asString()).isEqualTo("CEP_INVALID");
        assertThat(call("GET", "/api/shipping/quote?cep=22240003", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void cepLookup() throws Exception {
        String token = login(DataSeeder.CUSTOMER_EMAIL);
        HttpResponse<String> ok = call("GET", "/api/address/cep/22240-003", token, null);
        assertThat(ok.statusCode()).isEqualTo(200);
        JsonNode a = json.readTree(ok.body());
        assertThat(a.path("cep").asString()).isEqualTo("22240-003");
        assertThat(a.path("street").asString()).isEqualTo("Rua Pereira da Silva");
        assertThat(a.path("district").asString()).isEqualTo("Laranjeiras");
        assertThat(a.path("city").asString()).isEqualTo("Rio de Janeiro");
        assertThat(a.path("state").asString()).isEqualTo("RJ");

        HttpResponse<String> missing = call("GET", "/api/address/cep/99999999", token, null);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(json.readTree(missing.body()).path("code").asString()).isEqualTo("CEP_NOT_FOUND");

        HttpResponse<String> offline = call("GET", "/api/address/cep/" + TestCepConfig.OFFLINE, token, null);
        assertThat(offline.statusCode()).isEqualTo(503);
        assertThat(json.readTree(offline.body()).path("code").asString()).isEqualTo("CEP_UNAVAILABLE");

        HttpResponse<String> invalid = call("GET", "/api/address/cep/1234", token, null);
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(json.readTree(invalid.body()).path("code").asString()).isEqualTo("CEP_INVALID");
    }
}
