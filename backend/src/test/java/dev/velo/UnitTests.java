package dev.velo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.velo.auth.PasswordPolicy;
import dev.velo.common.ApiException;
import dev.velo.common.Csv;
import dev.velo.common.Texts;
import dev.velo.config.VeloProperties;
import dev.velo.integration.WebhookService;
import dev.velo.payment.Cards;
import dev.velo.payment.MercadoPagoWebhookTestAccess;
import dev.velo.shipping.CepService;
import dev.velo.shipping.ShippingQuotes;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class UnitTests {

    @Test
    void luhnAndBrands() {
        assertThat(Cards.luhn("4242424242424242")).isTrue();
        assertThat(Cards.luhn("4242424242424241")).isFalse();
        assertThat(Cards.brand("4242424242424242")).isEqualTo("Visa");
        assertThat(Cards.brand("5555555555554444")).isEqualTo("Mastercard");
        assertThat(Cards.brand("378282246310005")).isEqualTo("Amex");
        assertThat(Cards.expiry("12/30")).isEqualTo(YearMonth.of(2030, 12));
        assertThat(Cards.expiry("13/30")).isNull();
    }

    @Test
    void slugsAndLikes() {
        assertThat(Texts.slug("Café Especial em Grãos 250 g")).isEqualTo("cafe-especial-em-graos-250-g");
        assertThat(Texts.like("50%_off")).isEqualTo("%50\\%\\_off%");
    }

    @Test
    void passwordPolicy() {
        assertThatThrownBy(() -> PasswordPolicy.check("curta1", "a@b.com", "Ana")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("somenteletras", "a@b.com", "Ana")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("mariana2026", "mariana@b.com", "Mariana Souza")).isInstanceOf(ApiException.class);
        PasswordPolicy.check("Girassol42", "ana@b.com", "Ana Lima");
    }

    @Test
    void stripeSignature() throws Exception {
        String secret = "whsec_test";
        String payload = "{\"type\":\"checkout.session.completed\"}";
        long t = 1_800_000_000L;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = HexFormat.of().formatHex(mac.doFinal((t + "." + payload).getBytes(StandardCharsets.UTF_8)));
        assertThat(dev.velo.payment.StripeWebhookTestAccess.verify(payload, "t=" + t + ",v1=" + sig, secret, t + 10)).isTrue();
        assertThat(dev.velo.payment.StripeWebhookTestAccess.verify(payload, "t=" + t + ",v1=" + sig, secret, t + 1000)).isFalse();
        assertThat(dev.velo.payment.StripeWebhookTestAccess.verify(payload + " ", "t=" + t + ",v1=" + sig, secret, t)).isFalse();
    }

    private static String hmac(String secret, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void mercadoPagoSignature() throws Exception {
        String secret = "mp_segredo";
        long now = 1_800_000_000L;
        String ts = Long.toString(now);
        String manifest = "id:123456;request-id:req-1;ts:" + ts + ";";
        assertThat(MercadoPagoWebhookTestAccess.manifest("123456", "req-1", ts)).isEqualTo(manifest);
        String header = "ts=" + ts + ",v1=" + hmac(secret, manifest);
        assertThat(MercadoPagoWebhookTestAccess.verify("123456", "req-1", header, secret, now + 30)).isTrue();
        assertThat(MercadoPagoWebhookTestAccess.verify("123456", "req-1", header, "outro_segredo", now)).isFalse();
        assertThat(MercadoPagoWebhookTestAccess.verify("123456", "req-1", header, secret, now + 48 * 3600 + 1)).isFalse();
        assertThat(MercadoPagoWebhookTestAccess.verify("999999", "req-1", header, secret, now)).isFalse();
        assertThat(MercadoPagoWebhookTestAccess.verify("123456", "req-1", null, secret, now)).isFalse();
        assertThat(MercadoPagoWebhookTestAccess.verify("123456", "req-1", "ts=" + ts, secret, now)).isFalse();

        String millis = Long.toString(now * 1000);
        String msHeader = "ts=" + millis + ",v1=" + hmac(secret, "id:123456;request-id:req-1;ts:" + millis + ";");
        assertThat(MercadoPagoWebhookTestAccess.verify("123456", "req-1", msHeader, secret, now + 60)).isTrue();
        assertThat(MercadoPagoWebhookTestAccess.verify("123456", "req-1", msHeader, secret, now + 3 * 24 * 3600)).isFalse();

        String alnum = "ts=" + ts + ",v1=" + hmac(secret, "id:abc123;request-id:req-1;ts:" + ts + ";");
        assertThat(MercadoPagoWebhookTestAccess.verify("ABC123", "req-1", alnum, secret, now)).isTrue();
    }

    @Test
    void outgoingWebhookSignature() throws Exception {
        String body = "{\"id\":\"evt_1\",\"type\":\"test.ping\"}";
        String header = WebhookService.signature("whsec_velo", 1_800_000_000L, body);
        assertThat(header).matches("t=1800000000,v1=[0-9a-f]{64}");
        assertThat(header).isEqualTo("t=1800000000,v1=" + hmac("whsec_velo", "1800000000." + body));
        assertThat(WebhookService.EVENTS).contains("order.created", "order.paid", "order.shipped", "order.refunded", "test.ping");
    }

    @Test
    void providerAliases() {
        assertThat(new VeloProperties.Payments("mercadopago", 0, null, null).provider()).isEqualTo("MERCADO_PAGO");
        assertThat(new VeloProperties.Payments("mercado_pago", 0, null, null).provider()).isEqualTo("MERCADO_PAGO");
        assertThat(new VeloProperties.Payments(" stripe ", 0, null, null).provider()).isEqualTo("STRIPE");
        assertThat(new VeloProperties.Payments(null, 0, null, null).provider()).isEqualTo("SIMULATED");
        assertThat(new VeloProperties.Webhooks(" ", " ").configured()).isFalse();
    }

    @Test
    void shippingRegionsByCep() {
        assertThat(ShippingQuotes.destination("22240003")).isEqualTo(new ShippingQuotes.Destination("Sudeste", "RJ", 2, 4));
        assertThat(ShippingQuotes.destination("01310100")).isEqualTo(new ShippingQuotes.Destination("Sudeste", "SP", 2, 4));
        assertThat(ShippingQuotes.destination("30130010").state()).isEqualTo("MG");
        assertThat(ShippingQuotes.destination("80010000")).isEqualTo(new ShippingQuotes.Destination("Sul", "PR", 3, 5));
        assertThat(ShippingQuotes.destination("90010000").state()).isEqualTo("RS");
        assertThat(ShippingQuotes.destination("70040010")).isEqualTo(new ShippingQuotes.Destination("Centro-Oeste", "DF", 4, 7));
        assertThat(ShippingQuotes.destination("74000000").state()).isEqualTo("GO");
        assertThat(ShippingQuotes.destination("40010000")).isEqualTo(new ShippingQuotes.Destination("Nordeste", "BA", 5, 9));
        assertThat(ShippingQuotes.destination("60010000").state()).isEqualTo("CE");
        assertThat(ShippingQuotes.destination("65000000").state()).isEqualTo("MA");
        assertThat(ShippingQuotes.destination("66010000")).isEqualTo(new ShippingQuotes.Destination("Norte", "PA", 7, 12));
        assertThat(ShippingQuotes.destination("69010000").state()).isEqualTo("AM");
        assertThat(ShippingQuotes.destination("69900000")).isEqualTo(new ShippingQuotes.Destination("Norte", "AC", 7, 12));
        assertThat(CepService.digits("22.240-003")).isEqualTo("22240003");
        assertThatThrownBy(() -> CepService.digits("2224000")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> CepService.digits("abc22240003")).isInstanceOf(ApiException.class);
    }

    @Test
    void csvForExcel() {
        String out = new Csv().row("Nome", "Valor").row(Csv.text("=SOMA(A1)"), Csv.money(new BigDecimal("1234.5")))
                .row("a;b", "c\"d").toString();
        assertThat(out).startsWith("\uFEFFNome;Valor\r\n");
        assertThat(out).contains("'=SOMA(A1);1234,50\r\n");
        assertThat(out).endsWith("\"a;b\";\"c\"\"d\"\r\n");
        assertThat(Csv.money(null)).isEmpty();
    }
}
