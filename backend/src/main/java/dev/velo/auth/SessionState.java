package dev.velo.auth;

import java.time.Instant;

public record SessionState(VeloPrincipal principal, Instant idleExpiresAt, Instant expiresAt) {
}
