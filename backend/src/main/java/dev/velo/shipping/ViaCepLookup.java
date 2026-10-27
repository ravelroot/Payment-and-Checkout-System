package dev.velo.shipping;

import dev.velo.common.Texts;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ViaCepLookup implements CepLookup {

    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final JsonMapper json = JsonMapper.builder().build();

    @Override
    public Optional<CepAddress> find(String digits) {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://viacep.com.br/ws/" + digits + "/json/"))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 400) {
                return Optional.empty();
            }
            if (res.statusCode() >= 300) {
                throw new IllegalStateException("ViaCEP respondeu HTTP " + res.statusCode());
            }
            JsonNode n = json.readTree(res.body());
            JsonNode erro = n.path("erro");
            if (erro.isBoolean() ? erro.booleanValue() : "true".equalsIgnoreCase(erro.asString(""))) {
                return Optional.empty();
            }
            return Optional.of(new CepAddress(CepService.format(digits), Texts.clean(n.path("logradouro").asString("")),
                    Texts.clean(n.path("bairro").asString("")), Texts.clean(n.path("localidade").asString("")),
                    Texts.clean(n.path("uf").asString(""))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Consulta de CEP interrompida", e);
        }
    }
}
