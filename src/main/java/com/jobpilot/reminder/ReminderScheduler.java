package com.jobpilot.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "jobpilot.reminders.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderProcessor reminderProcessor;

    public ReminderScheduler(ReminderProcessor reminderProcessor) {
        this.reminderProcessor = reminderProcessor;
    }

    @Scheduled(fixedDelayString = "${jobpilot.reminders.scheduler.fixed-delay-ms:60000}")
    public void tick() {
        int processed = reminderProcessor.processDueReminders();
        if (processed > 0) {
            log.info("Reminder scheduler processed {} due reminder(s)", processed);
        }
    }
}
