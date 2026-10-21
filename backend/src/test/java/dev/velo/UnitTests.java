package dev.velo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.velo.auth.PasswordPolicy;
import dev.velo.common.ApiException;
import dev.velo.common.Texts;
import dev.velo.payment.Cards;
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
}
