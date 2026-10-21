package dev.velo.auth;

import dev.velo.common.ApiException;
import dev.velo.common.Texts;
import dev.velo.user.User;
import dev.velo.user.UserRepository;
import dev.velo.user.UserRole;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    static final int MAX_FAILURES = 5;
    static final int LOCK_MINUTES = 5;

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SessionService sessions;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, SessionService sessions, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.sessions = sessions;
        this.clock = clock;
        this.dummyHash = encoder.encode("velo-timing-guard-2026");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public SessionService.Issued login(String email, String password, String ip, String userAgent) {
        String normalized = Texts.email(email);
        User user = normalized == null ? null : users.findByEmail(normalized).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (user == null) {
            encoder.matches(password == null ? "" : password, dummyHash);
            throw invalid();
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOCKED",
                    "Muitas tentativas. Aguarde alguns minutos e tente de novo.");
        }
        if (!encoder.matches(password == null ? "" : password, user.getPasswordHash())) {
            registerFailure(user, now);
            throw invalid();
        }
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INACTIVE", "Esta conta está desativada. Fale com um administrador.");
        }
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        users.save(user);
        return sessions.open(user, ip, userAgent);
    }

    private void registerFailure(User user, LocalDateTime now) {
        int n = user.getFailedLogins() + 1;
        user.setFailedLogins(n);
        if (n >= MAX_FAILURES) {
            user.setLockedUntil(now.plusMinutes(LOCK_MINUTES));
            user.setFailedLogins(0);
        }
        users.save(user);
    }

    @Transactional
    public SessionService.Issued register(String fullName, String email, String password, String ip, String userAgent) {
        String normalized = Texts.email(email);
        if (users.existsByEmail(normalized)) {
            throw ApiException.conflict("EMAIL_TAKEN", "Já existe uma conta com este e-mail.");
        }
        PasswordPolicy.check(password, normalized, fullName);
        User u = new User();
        u.setFullName(Texts.clean(fullName));
        u.setEmail(normalized);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(UserRole.CUSTOMER);
        u.setActive(true);
        u.setLastLoginAt(LocalDateTime.now(clock));
        users.save(u);
        return sessions.open(u, ip, userAgent);
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "E-mail ou senha incorretos.");
    }
}
