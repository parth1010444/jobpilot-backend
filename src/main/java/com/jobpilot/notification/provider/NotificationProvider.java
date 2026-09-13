package com.jobpilot.notification.provider;

import com.jobpilot.notification.Notification;

/**
 * Pluggable delivery channel. Phase 8 ships an in-app provider; Phase 9 adds email + retries.
 */
public interface NotificationProvider {

    String channel();

    void send(Notification notification);
}
