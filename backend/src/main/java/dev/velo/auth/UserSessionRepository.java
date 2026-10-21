package dev.velo.auth;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByTokenHash(String tokenHash);

    List<UserSession> findByUserIdAndEndedAtIsNull(Long userId);

    @Query("select s from UserSession s where s.endedAt is null and (s.lastSeenAt < :idle or s.createdAt < :max)")
    List<UserSession> findStale(@Param("idle") LocalDateTime idleLimit, @Param("max") LocalDateTime maxLimit);

    @Modifying
    @Query("delete from UserSession s where s.endedAt is not null and s.endedAt < :before")
    int purgeEndedBefore(@Param("before") LocalDateTime before);
}
