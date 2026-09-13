package com.jobpilot.reminder;

import com.jobpilot.notification.NotificationService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes due PENDING reminders idempotently.
 *
 * <p>Claim step: {@code UPDATE ... SET status=PROCESSED WHERE id=? AND status=PENDING}.
 * Only the winner (update count = 1) creates a notification, so a double scheduler tick
 * cannot duplicate in-app notifications. {@code notifications.reminder_id} UNIQUE is a
 * second line of defense.
 */
@Service
public class ReminderProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReminderProcessor.class);

    private final ReminderRepository reminderRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public ReminderProcessor(
            ReminderRepository reminderRepository,
            NotificationService notificationService,
            Clock clock
    ) {
        this.reminderRepository = reminderRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional
    public int processDueReminders() {
        Instant now = clock.instant();
        List<UUID> dueIds = reminderRepository.findDuePendingIds(now);
        int processed = 0;
        for (UUID id : dueIds) {
            if (processOne(id, now)) {
                processed++;
            }
        }
        return processed;
    }

    /**
     * @return true if this call claimed and notified the reminder
     */
    @Transactional
    public boolean processOne(UUID reminderId, Instant now) {
        int claimed = reminderRepository.claimPending(reminderId, now);
        if (claimed == 0) {
            log.debug("Reminder {} already claimed or not PENDING — skip", reminderId);
            return false;
        }

        Reminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new IllegalStateException("Claimed reminder missing: " + reminderId));

        notificationService.createFromReminder(reminder);
        log.info("Processed reminder {} for user {}", reminderId, reminder.getUserId());
        return true;
    }
}
