package dev.velo.auth;

import dev.velo.config.VeloProperties;
import dev.velo.user.User;
import dev.velo.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    public enum Failure { UNAUTHENTICATED, SESSION_IDLE, SESSION_EXPIRED }

    public record Validation(SessionState state, Failure failure) {
    }

    public record Issued(String token, SessionState state) {
    }

    private static final Duration TOUCH_GRANULARITY = Duration.ofSeconds(5);

    private final UserSessionRepository sessions;
    private final UserRepository users;
    private final Clock clock;
    private final Duration idle;
    private final Duration max;
    private final SecureRandom random = new SecureRandom();

    public SessionService(UserSessionRepository sessions, UserRepository users, Clock clock, VeloProperties props) {
        this.sessions = sessions;
        this.users = users;
        this.clock = clock;
        this.idle = Duration.ofMinutes(props.session().idleMinutes());
        this.max = Duration.ofMinutes(props.session().maxMinutes());
    }

    public Duration idleLimit() {
        return idle;
    }

    public Duration maxLimit() {
        return max;
    }

    @Transactional
    public Issued open(User user, String ip, String userAgent) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        LocalDateTime now = now();
        UserSession s = new UserSession();
        s.setUserId(user.getId());
        s.setTokenHash(hash(token));
        s.setCreatedAt(now);
        s.setLastSeenAt(now);
        s.setIpAddress(truncate(ip, 45));
        s.setUserAgent(truncate(userAgent, 255));
        sessions.save(s);
        return new Issued(token, state(s, user));
    }

    @Transactional
    public Validation validate(String token, boolean touch) {
        if (token == null || token.isBlank()) {
            return new Validation(null, Failure.UNAUTHENTICATED);
        }
        Optional<UserSession> found = sessions.findByTokenHash(hash(token));
        if (found.isEmpty() || found.get().getEndedAt() != null) {
            Failure f = found.map(s -> "IDLE".equals(s.getEndReason()) ? Failure.SESSION_IDLE
                    : "EXPIRED".equals(s.getEndReason()) ? Failure.SESSION_EXPIRED : Failure.UNAUTHENTICATED)
                    .orElse(Failure.UNAUTHENTICATED);
            return new Validation(null, f);
        }
        UserSession s = found.get();
        LocalDateTime now = now();
        if (!now.isBefore(s.getCreatedAt().plus(max))) {
            end(s, "EXPIRED", now);
            return new Validation(null, Failure.SESSION_EXPIRED);
        }
        if (!now.isBefore(s.getLastSeenAt().plus(idle))) {
            end(s, "IDLE", now);
            return new Validation(null, Failure.SESSION_IDLE);
        }
        User user = users.findById(s.getUserId()).orElse(null);
        if (user == null || !user.isActive()) {
            end(s, "REVOKED", now);
            return new Validation(null, Failure.UNAUTHENTICATED);
        }
        if (touch && Duration.between(s.getLastSeenAt(), now).compareTo(TOUCH_GRANULARITY) >= 0) {
            s.setLastSeenAt(now);
            sessions.save(s);
        }
        return new Validation(state(s, user), null);
    }

    @Transactional
    public void close(Long sessionId) {
        sessions.findById(sessionId).filter(s -> s.getEndedAt() == null).ifPresent(s -> end(s, "LOGOUT", now()));
    }

    @Transactional
    public void closeByToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessions.findByTokenHash(hash(token)).filter(s -> s.getEndedAt() == null).ifPresent(s -> end(s, "LOGOUT", now()));
    }

    @Transactional
    public int revokeAll(Long userId) {
        LocalDateTime now = now();
        int n = 0;
        for (UserSession s : sessions.findByUserIdAndEndedAtIsNull(userId)) {
            end(s, "REVOKED", now);
            n++;
        }
        return n;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    @Transactional
    public void sweep() {
        LocalDateTime now = now();
        for (UserSession s : sessions.findStale(now.minus(idle), now.minus(max))) {
            end(s, s.getCreatedAt().plus(max).isAfter(now) ? "IDLE" : "EXPIRED", now);
        }
        sessions.purgeEndedBefore(now.minusDays(7));
    }

    private void end(UserSession s, String reason, LocalDateTime now) {
        s.setEndedAt(now);
        s.setEndReason(reason);
        sessions.save(s);
    }

    private SessionState state(UserSession s, User user) {
        VeloPrincipal p = new VeloPrincipal(user.getId(), s.getId(), user.getEmail(), user.getFullName(), user.getRole());
        LocalDateTime idleAt = s.getLastSeenAt().plus(idle);
        LocalDateTime maxAt = s.getCreatedAt().plus(max);
        return new SessionState(p, (idleAt.isBefore(maxAt) ? idleAt : maxAt).toInstant(ZoneOffset.UTC), maxAt.toInstant(ZoneOffset.UTC));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    static String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String v, int n) {
        return v == null ? null : v.length() <= n ? v : v.substring(0, n);
    }
}
