package com.jobpilot.notification.provider;

import com.jobpilot.notification.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub for Phase 9 (real SMTP / retries / Kafka). Does not send mail yet.
 */
@Component
public class EmailNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationProvider.class);

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public void send(Notification notification) {
        log.debug(
                "Email provider stub — skipping send for notification {} (Phase 9)",
                notification.getId()
        );
    }
}
