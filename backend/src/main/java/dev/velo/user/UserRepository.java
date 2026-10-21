package dev.velo.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRoleAndActiveTrue(UserRole role);

    @Query("select u.id from User u where lower(u.fullName) like :q escape '\\' or lower(u.email) like :q escape '\\'")
    List<Long> idsMatching(@Param("q") String like, Pageable page);
}
