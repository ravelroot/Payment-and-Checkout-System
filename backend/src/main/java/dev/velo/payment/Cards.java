package dev.velo.payment;

import dev.velo.common.Texts;
import java.time.YearMonth;

public final class Cards {

    private Cards() {
    }

    public static boolean luhn(String digits) {
        if (digits == null || digits.length() < 12 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (d < 0 || d > 9) {
                return false;
            }
            if (dbl) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    public static String brand(String digits) {
        if (digits.matches("^(636368|438935|504175|451416|636297|5067|4576|4011|506699).*")) {
            return "Elo";
        }
        if (digits.startsWith("606282") || digits.startsWith("3841")) {
            return "Hipercard";
        }
        if (digits.startsWith("34") || digits.startsWith("37")) {
            return "Amex";
        }
        if (digits.startsWith("4")) {
            return "Visa";
        }
        if (digits.matches("^(5[1-5]|2(2[2-9]|[3-6]\\d|7[01]|720)).*")) {
            return "Mastercard";
        }
        return "Cartão";
    }

    public static YearMonth expiry(String value) {
        String d = Texts.digits(value);
        try {
            if (d.length() == 4) {
                return YearMonth.of(2000 + Integer.parseInt(d.substring(2)), Integer.parseInt(d.substring(0, 2)));
            }
            if (d.length() == 6) {
                return YearMonth.of(Integer.parseInt(d.substring(2)), Integer.parseInt(d.substring(0, 2)));
            }
        } catch (java.time.DateTimeException | NumberFormatException e) {
            return null;
        }
        return null;
    }
}
