package com.jobpilot.notification.provider;

import com.jobpilot.notification.Notification;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Marks the notification as SENT immediately — content is available via GET /api/notifications.
 */
@Component
public class InAppNotificationProvider implements NotificationProvider {

    private final Clock clock;

    public InAppNotificationProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String channel() {
        return "IN_APP";
    }

    @Override
    public void send(Notification notification) {
        notification.markSent(clock.instant());
    }
}
