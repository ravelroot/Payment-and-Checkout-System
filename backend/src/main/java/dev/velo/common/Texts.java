package dev.velo.common;

import java.text.Normalizer;
import java.util.Locale;

public final class Texts {

    private Texts() {
    }

    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String v = value.strip().replaceAll("\\s+", " ");
        return v.isEmpty() ? null : v;
    }

    public static String email(String value) {
        String v = clean(value);
        return v == null ? null : v.toLowerCase(Locale.ROOT);
    }

    public static String slug(String value) {
        String n = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String s = n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "item" : s;
    }

    public static String like(String value) {
        String v = clean(value);
        if (v == null) {
            return null;
        }
        return "%" + v.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    public static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
