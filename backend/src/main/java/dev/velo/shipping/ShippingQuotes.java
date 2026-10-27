package dev.velo.shipping;

/**
 * Região e prazo estimados a partir das faixas de CEP dos Correios, sem chamada externa.
 */
public final class ShippingQuotes {

    public record Destination(String region, String state, int minDays, int maxDays) {
    }

    private ShippingQuotes() {
    }

    public static Destination destination(String digits) {
        int prefix = Integer.parseInt(digits.substring(0, 5));
        String uf = state(prefix);
        String region = uf == null ? regionByFirstDigit(digits.charAt(0)) : region(uf);
        return switch (region) {
            case "Sudeste" -> new Destination(region, uf, 2, 4);
            case "Sul" -> new Destination(region, uf, 3, 5);
            case "Centro-Oeste" -> new Destination(region, uf, 4, 7);
            case "Nordeste" -> new Destination(region, uf, 5, 9);
            default -> new Destination("Norte", uf, 7, 12);
        };
    }

    static String state(int p) {
        if (p < 20000) {
            return "SP";
        } else if (p <= 28999) {
            return "RJ";
        } else if (p <= 29999) {
            return "ES";
        } else if (p <= 39999) {
            return "MG";
        } else if (p <= 48999) {
            return "BA";
        } else if (p <= 49999) {
            return "SE";
        } else if (p <= 56999) {
            return "PE";
        } else if (p <= 57999) {
            return "AL";
        } else if (p <= 58999) {
            return "PB";
        } else if (p <= 59999) {
            return "RN";
        } else if (p <= 63999) {
            return "CE";
        } else if (p <= 64999) {
            return "PI";
        } else if (p <= 65999) {
            return "MA";
        } else if (p <= 68899) {
            return "PA";
        } else if (p <= 68999) {
            return "AP";
        } else if (p <= 69299) {
            return "AM";
        } else if (p <= 69399) {
            return "RR";
        } else if (p <= 69899) {
            return "AM";
        } else if (p <= 69999) {
            return "AC";
        } else if (p <= 72799) {
            return "DF";
        } else if (p <= 72999) {
            return "GO";
        } else if (p <= 73699) {
            return "DF";
        } else if (p <= 76799) {
            return "GO";
        } else if (p <= 76999) {
            return "RO";
        } else if (p <= 77999) {
            return "TO";
        } else if (p <= 78899) {
            return "MT";
        } else if (p <= 78999) {
            return null;
        } else if (p <= 79999) {
            return "MS";
        } else if (p <= 87999) {
            return "PR";
        } else if (p <= 89999) {
            return "SC";
        }
        return "RS";
    }

    static String region(String uf) {
        return switch (uf) {
            case "SP", "RJ", "ES", "MG" -> "Sudeste";
            case "PR", "SC", "RS" -> "Sul";
            case "DF", "GO", "MT", "MS" -> "Centro-Oeste";
            case "BA", "SE", "PE", "AL", "PB", "RN", "CE", "PI", "MA" -> "Nordeste";
            default -> "Norte";
        };
    }

    private static String regionByFirstDigit(char first) {
        return switch (first) {
            case '0', '1', '2', '3' -> "Sudeste";
            case '4', '5' -> "Nordeste";
            case '7' -> "Centro-Oeste";
            case '8', '9' -> "Sul";
            default -> "Norte";
        };
    }
}
