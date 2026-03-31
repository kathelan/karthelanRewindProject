package pl.kathelan.dbperf.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import pl.kathelan.dbperf.entity.AuthProcess;
import pl.kathelan.dbperf.entity.AuthProcessStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AuthProcessRepository extends JpaRepository<AuthProcess, UUID> {

    Optional<AuthProcess> findFirstByUserIdAndStatus(String userId, AuthProcessStatus status);

    @Modifying
    @Transactional
    @Query("""
            UPDATE AuthProcess a
            SET a.status = :newStatus, a.updatedAt = :now
            WHERE a.userId = :userId AND a.status = :oldStatus
            """)
    int updateByUserIdAndStatus(
            @Param("userId") String userId,
            @Param("oldStatus") AuthProcessStatus oldStatus,
            @Param("newStatus") AuthProcessStatus newStatus,
            @Param("now") LocalDateTime now
    );
}