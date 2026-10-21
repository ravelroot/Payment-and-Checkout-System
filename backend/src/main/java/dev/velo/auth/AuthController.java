package dev.velo.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final SessionService sessions;

    public AuthController(AuthService auth, SessionService sessions) {
        this.auth = auth;
        this.sessions = sessions;
    }

    @PostMapping("/login")
    public AuthDtos.SessionInfo login(@Valid @RequestBody AuthDtos.LoginRequest body, HttpServletRequest request) {
        return info(auth.login(body.email(), body.password(), request.getRemoteAddr(), request.getHeader("User-Agent")));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.SessionInfo register(@Valid @RequestBody AuthDtos.RegisterRequest body, HttpServletRequest request) {
        return info(auth.register(body.fullName(), body.email(), body.password(), request.getRemoteAddr(),
                request.getHeader("User-Agent")));
    }

    @GetMapping("/me")
    public AuthDtos.Me me() {
        return me(CurrentUser.get());
    }

    @PostMapping("/ping")
    public ResponseEntity<Void> ping() {
        CurrentUser.get();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, @RequestBody(required = false) String body) {
        String t = SessionAuthFilter.token(request);
        sessions.closeByToken(t != null ? t : body == null ? null : body.trim());
        return ResponseEntity.noContent().build();
    }

    private AuthDtos.SessionInfo info(SessionService.Issued issued) {
        SessionState s = issued.state();
        return new AuthDtos.SessionInfo(issued.token(), me(s.principal()), s.idleExpiresAt(), s.expiresAt(),
                (int) sessions.idleLimit().toMinutes(), (int) sessions.maxLimit().toMinutes());
    }

    private static AuthDtos.Me me(VeloPrincipal p) {
        return new AuthDtos.Me(p.userId(), p.fullName(), p.email(), p.role());
    }
}
