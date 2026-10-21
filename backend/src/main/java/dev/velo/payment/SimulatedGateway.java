package dev.velo.payment;

import dev.velo.common.Money;
import dev.velo.common.Texts;
import dev.velo.order.Order;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class SimulatedGateway implements PaymentGateway {

    public static final String DECLINED = "4000000000000002";
    public static final String NO_FUNDS = "4000000000009995";

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public SimulatedGateway(Clock clock) {
        this.clock = clock;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.SIMULATED;
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CARD || method == PaymentMethod.PIX || method == PaymentMethod.BOLETO;
    }

    @Override
    public GatewayResult start(Order order, PaymentRequest request) {
        return switch (request.method()) {
            case CARD -> card(request.card());
            case PIX -> pix(order);
            case BOLETO -> boleto(order);
            default -> GatewayResult.failed("Forma de pagamento indisponível.");
        };
    }

    private GatewayResult card(CardInput card) {
        if (card == null) {
            return GatewayResult.failed("Informe os dados do cartão.");
        }
        String digits = Texts.digits(card.number());
        String holder = Texts.clean(card.holder());
        if (holder == null || holder.length() < 3) {
            return GatewayResult.failed("Informe o nome impresso no cartão.");
        }
        if (!Cards.luhn(digits)) {
            return GatewayResult.failed("Número de cartão inválido.");
        }
        YearMonth exp = Cards.expiry(card.expiry());
        if (exp == null || exp.isBefore(YearMonth.now(clock))) {
            return GatewayResult.failed("Validade inválida ou vencida.");
        }
        String brand = Cards.brand(digits);
        String cvv = Texts.digits(card.cvv());
        int cvvLength = "Amex".equals(brand) ? 4 : 3;
        if (cvv.length() != cvvLength) {
            return GatewayResult.failed("Código de segurança inválido.");
        }
        String last4 = digits.substring(digits.length() - 4);
        if (DECLINED.equals(digits)) {
            return new GatewayResult(PaymentStatus.FAILED, id("ch"), null, brand, last4, null, null,
                    "Cartão recusado pelo emissor.", null);
        }
        if (NO_FUNDS.equals(digits)) {
            return new GatewayResult(PaymentStatus.FAILED, id("ch"), null, brand, last4, null, null, "Saldo insuficiente.", null);
        }
        return new GatewayResult(PaymentStatus.PAID, id("ch"), null, brand, last4, null, null, null, null);
    }

    private GatewayResult pix(Order order) {
        String txid = id("pix").replace("_", "").toUpperCase();
        String amount = Money.of(order.getTotal()).toPlainString();
        String code = "00020126580014BR.GOV.BCB.PIX0136velo-simulado-" + txid.substring(0, 12).toLowerCase()
                + "520400005303986540" + amount.length() + amount + "5802BR5904VELO6014RIO DE JANEIRO62"
                + String.format("%02d", txid.length() + 4) + "05" + String.format("%02d", txid.length()) + txid + "6304"
                + HexFormat.of().withUpperCase().toHexDigits((short) txid.hashCode());
        return new GatewayResult(PaymentStatus.PENDING, txid, null, null, null, code, null, null,
                LocalDateTime.now(clock).plusMinutes(30));
    }

    private GatewayResult boleto(Order order) {
        long cents = Money.cents(order.getTotal());
        String value = String.format("%010d", cents);
        long factor = 10000 + (LocalDateTime.now(clock).plusDays(3).toLocalDate().toEpochDay() % 9000);
        String n = String.format("%05d", Math.abs(random.nextInt(100000)));
        String line = "00190.00009 0" + n.substring(0, 4) + "." + n.substring(4) + "0001 " + order.getId() % 100000
                + "00.000000 1 " + factor + value;
        return new GatewayResult(PaymentStatus.PENDING, id("bol"), null, null, null, null,
                line.length() > 60 ? line.substring(0, 60) : line, null, LocalDateTime.now(clock).plusDays(3));
    }

    @Override
    public boolean refund(Payment payment, BigDecimal amount) {
        return true;
    }

    private String id(String prefix) {
        byte[] b = new byte[10];
        random.nextBytes(b);
        return prefix + "_" + HexFormat.of().formatHex(b);
    }
}
