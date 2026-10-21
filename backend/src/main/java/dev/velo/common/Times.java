package dev.velo.common;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class Times {

    private Times() {
    }

    public static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
