package dev.velo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.velo.auth.AuthService;
import dev.velo.auth.SessionService;
import dev.velo.seed.DataSeeder;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@VeloTest
class SessionLimitsTest {

    @Autowired
    AuthService auth;

    @Autowired
    SessionService sessions;

    @Autowired
    MutableClock clock;

    @AfterEach
    void reset() {
        clock.reset();
    }

    @Test
    void idleSessionEndsAfterTwentyMinutes() {
        String token = auth.login(DataSeeder.CUSTOMER_EMAIL, DataSeeder.DEMO_PASSWORD, "127.0.0.1", "test").token();
        clock.advance(Duration.ofMinutes(5));
        assertThat(sessions.validate(token, true).state()).isNotNull();
        clock.advance(Duration.ofMinutes(19));
        assertThat(sessions.validate(token, false).state()).isNotNull();
        clock.advance(Duration.ofMinutes(1));
        SessionService.Validation v = sessions.validate(token, true);
        assertThat(v.state()).isNull();
        assertThat(v.failure()).isEqualTo(SessionService.Failure.SESSION_IDLE);
        assertThat(sessions.validate(token, true).failure()).isEqualTo(SessionService.Failure.SESSION_IDLE);
    }

    @Test
    void activeSessionEndsAfterThirtyMinutes() {
        String token = auth.login(DataSeeder.ADMIN_EMAIL, DataSeeder.DEMO_PASSWORD, "127.0.0.1", "test").token();
        for (int i = 0; i < 5; i++) {
            clock.advance(Duration.ofMinutes(5));
            assertThat(sessions.validate(token, true).state()).isNotNull();
        }
        clock.advance(Duration.ofMinutes(5));
        assertThat(sessions.validate(token, true).failure()).isEqualTo(SessionService.Failure.SESSION_EXPIRED);
    }

    @Test
    void passiveChecksDoNotKeepTheSessionAlive() {
        String token = auth.login(DataSeeder.STAFF_EMAIL, DataSeeder.DEMO_PASSWORD, "127.0.0.1", "test").token();
        clock.advance(Duration.ofMinutes(15));
        assertThat(sessions.validate(token, false).state()).isNotNull();
        clock.advance(Duration.ofMinutes(6));
        assertThat(sessions.validate(token, false).failure()).isEqualTo(SessionService.Failure.SESSION_IDLE);
    }

    @Test
    void logoutEndsTheSession() {
        String token = auth.login(DataSeeder.CUSTOMER_EMAIL, DataSeeder.DEMO_PASSWORD, "127.0.0.1", "test").token();
        sessions.closeByToken(token);
        assertThat(sessions.validate(token, true).state()).isNull();
    }
}
