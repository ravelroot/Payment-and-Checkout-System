package dev.velo.config;

import dev.velo.auth.SessionAuthFilter;
import dev.velo.auth.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SessionService sessions) throws Exception {
        http.csrf(c -> c.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .contentSecurityPolicy(c -> c.policyDirectives(
                                "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                                        + "font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; "
                                        + "form-action 'self' https://checkout.stripe.com")))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook/stripe").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers("/api/admin/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> unauthorized(req, res))
                        .accessDeniedHandler((req, res, ex) -> write(res, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                                "Seu perfil não permite esta ação.")))
                .addFilterBefore(new SessionAuthFilter(sessions), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void unauthorized(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Object failure = req.getAttribute(SessionAuthFilter.FAILURE_ATTRIBUTE);
        String code = failure == null ? "UNAUTHENTICATED" : failure.toString();
        String message = switch (code) {
            case "SESSION_IDLE" -> "Sua sessão terminou por inatividade. Entre novamente.";
            case "SESSION_EXPIRED" -> "Sua sessão atingiu o tempo máximo. Entre novamente.";
            default -> "Entre para continuar.";
        };
        write(res, HttpServletResponse.SC_UNAUTHORIZED, code, message);
    }

    private static void write(HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message.replace("\"", "'") + "\",\"fields\":{}}");
    }
}
