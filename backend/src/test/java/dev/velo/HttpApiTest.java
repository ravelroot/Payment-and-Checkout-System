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
}
