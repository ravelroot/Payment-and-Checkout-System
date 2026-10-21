package dev.velo.auth;

import dev.velo.user.UserRole;

public record VeloPrincipal(Long userId, Long sessionId, String email, String fullName, UserRole role) {
}
