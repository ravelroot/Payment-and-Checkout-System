package dev.velo.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionAuthFilter extends OncePerRequestFilter {

    public static final String FAILURE_ATTRIBUTE = "velo.session.failure";
    public static final String IDLE_HEADER = "X-Velo-Idle-Expires-At";
    public static final String MAX_HEADER = "X-Velo-Expires-At";

    private final SessionService sessions;

    public SessionAuthFilter(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = token(request);
        if (token != null) {
            boolean touch = !"true".equals(request.getHeader("X-Velo-Passive"));
            SessionService.Validation v = sessions.validate(token, touch);
            if (v.state() != null) {
                VeloPrincipal p = v.state().principal();
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(p, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + p.role().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
                response.setHeader(IDLE_HEADER, String.valueOf(v.state().idleExpiresAt().toEpochMilli()));
                response.setHeader(MAX_HEADER, String.valueOf(v.state().expiresAt().toEpochMilli()));
            } else {
                request.setAttribute(FAILURE_ATTRIBUTE, v.failure());
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    static String token(HttpServletRequest request) {
        String h = request.getHeader("Authorization");
        if (h != null && h.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String t = h.substring(7).trim();
            return t.isEmpty() ? null : t;
        }
        return null;
    }
}
