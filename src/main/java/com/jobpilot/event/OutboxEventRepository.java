package com.jobpilot.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE e.status = com.jobpilot.event.OutboxEventStatus.PENDING
            ORDER BY e.createdAt ASC
            """)
    List<OutboxEvent> findPending(Pageable pageable);

    List<OutboxEvent> findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
            String aggregateType,
            UUID aggregateId
    );

    long countByStatus(OutboxEventStatus status);

    /**
     * Mark PUBLISHED only while still PENDING (optimistic claim after successful Kafka send).
     * Returns 1 if this caller won, 0 if already published/failed by another worker.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OutboxEvent e
            SET e.status = com.jobpilot.event.OutboxEventStatus.PUBLISHED,
                e.publishedAt = :publishedAt,
                e.lastError = null
            WHERE e.id = :id
              AND e.status = com.jobpilot.event.OutboxEventStatus.PENDING
            """)
    int markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);
}
