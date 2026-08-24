package com.voltstack.ecommerce.identity.repository;

import com.voltstack.ecommerce.identity.model.VerificationToken;
import com.voltstack.ecommerce.identity.model.enums.VerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    List<VerificationToken> findByUserIdAndPurposeAndUsedAtIsNull(UUID userId, VerificationPurpose purpose);

    @Modifying
    @Query("UPDATE VerificationToken v SET v.usedAt = :now WHERE v.tokenHash = :tokenHash " +
            "AND v.usedAt IS NULL AND v.expiresAt > :now AND v.purpose = :purpose")
    int consume(@Param("tokenHash") String tokenHash, @Param("purpose") VerificationPurpose purpose,
                @Param("now") Instant now);
}
