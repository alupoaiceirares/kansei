package org.kansei.shieldwall.repository;

import org.kansei.shieldwall.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<User> findByActiveFalseAndDeactivatedAtBefore(Instant cutoff);

    // Friend-search: excludes deactivated accounts, caller supplies the page size via Pageable
    List<User> findByActiveTrueAndUsernameContainingIgnoreCase(String username, Pageable pageable);

    // active, never verified, and no unexpired/unused EMAIL_VERIFICATION token left as a recent /verify-email/resend may keep the account alive
    @Query("""
            SELECT u FROM User u
            WHERE u.active = true
              AND u.emailVerified = false
              AND NOT EXISTS (
                  SELECT 1 FROM VerificationToken vt
                  WHERE vt.user = u
                    AND vt.type = org.kansei.shieldwall.model.TokenType.EMAIL_VERIFICATION
                    AND vt.usedAt IS NULL
                    AND vt.expiresAt > :now
              )
            """)
    List<User> findUnverifiedWithNoValidToken(@Param("now") Instant now);
}