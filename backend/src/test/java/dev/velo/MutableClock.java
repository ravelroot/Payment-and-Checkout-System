package dev.velo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public class MutableClock extends Clock {

    private Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(now, zone);
    }

    @Override
    public Instant instant() {
        return now;
    }

    public void advance(Duration d) {
        now = now.plus(d);
    }

    public void reset() {
        now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
