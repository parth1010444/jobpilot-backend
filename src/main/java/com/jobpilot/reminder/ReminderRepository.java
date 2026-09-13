package com.jobpilot.reminder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    Page<Reminder> findByUserId(UUID userId, Pageable pageable);

    Page<Reminder> findByUserIdAndStatus(UUID userId, ReminderStatus status, Pageable pageable);

    Optional<Reminder> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            SELECT r.id FROM Reminder r
            WHERE r.status = com.jobpilot.reminder.ReminderStatus.PENDING
              AND r.scheduledAt <= :now
            ORDER BY r.scheduledAt ASC
            """)
    List<UUID> findDuePendingIds(@Param("now") Instant now);

    /**
     * Optimistic claim: only one concurrent worker can flip PENDING → PROCESSED.
     * Returns 1 if this caller won the claim, 0 if already claimed/cancelled.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Reminder r
            SET r.status = com.jobpilot.reminder.ReminderStatus.PROCESSED,
                r.completedAt = :now
            WHERE r.id = :id
              AND r.status = com.jobpilot.reminder.ReminderStatus.PENDING
            """)
    int claimPending(@Param("id") UUID id, @Param("now") Instant now);
}
