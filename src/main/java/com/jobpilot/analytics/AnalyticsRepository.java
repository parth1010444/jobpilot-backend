package com.jobpilot.analytics;

import com.jobpilot.application.Application;
import com.jobpilot.application.ApplicationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Read-only aggregation queries over the current user's applications /
 * interviews. Backed by the {@code applications} entity mapping.
 */
public interface AnalyticsRepository extends JpaRepository<Application, UUID> {

    long countByUserId(UUID userId);

    long countByUserIdAndStatus(UUID userId, ApplicationStatus status);

    long countByUserIdAndStatusNotIn(UUID userId, List<ApplicationStatus> statuses);

    @Query("""
            SELECT a.status, COUNT(a)
            FROM Application a
            WHERE a.userId = :userId
            GROUP BY a.status
            """)
    List<Object[]> countGroupedByStatus(@Param("userId") UUID userId);

    @Query("""
            SELECT COUNT(i)
            FROM Interview i
            WHERE i.application.userId = :userId
            """)
    long countInterviewsByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT a
            FROM Application a
            WHERE a.userId = :userId
              AND (
                    (a.createdAt >= :fromInclusive AND a.createdAt < :toExclusive)
                 OR (a.appliedAt IS NOT NULL AND a.appliedAt >= :fromInclusive AND a.appliedAt < :toExclusive)
              )
            """)
    List<Application> findForTimeline(
            @Param("userId") UUID userId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive
    );

    @Query("""
            SELECT a.id
            FROM Application a
            WHERE a.userId = :userId
            """)
    List<UUID> findApplicationIdsByUserId(@Param("userId") UUID userId);
}
