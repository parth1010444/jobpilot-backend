package com.jobpilot.notification;

import com.jobpilot.common.error.JobPilotException;
import com.jobpilot.notification.dto.NotificationResponse;
import com.jobpilot.notification.provider.InAppNotificationProvider;
import com.jobpilot.reminder.Reminder;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final InAppNotificationProvider inAppNotificationProvider;

    public NotificationService(
            NotificationRepository notificationRepository,
            InAppNotificationProvider inAppNotificationProvider
    ) {
        this.notificationRepository = notificationRepository;
        this.inAppNotificationProvider = inAppNotificationProvider;
    }

    /**
     * Creates an in-app notification for a claimed reminder and marks it SENT.
     * Caller must have already claimed the reminder (PENDING → PROCESSED).
     * Defense in depth: unique(reminder_id) + exists check prevent duplicates.
     */
    @Transactional
    public Notification createFromReminder(Reminder reminder) {
        var existing = notificationRepository.findByReminderId(reminder.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        String message = reminder.getDescription() != null
                ? reminder.getDescription()
                : "Reminder: " + reminder.getTitle();

        Notification notification = new Notification(
                reminder.getUserId(),
                reminder.getId(),
                NotificationType.REMINDER,
                reminder.getTitle(),
                message
        );
        inAppNotificationProvider.send(notification);
        return notificationRepository.saveAndFlush(notification);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(UUID userId, Pageable pageable) {
        validateSort(pageable.getSort());
        return notificationRepository.findByUserId(userId, pageable).map(NotificationResponse::from);
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID id) {
        Notification notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new JobPilotException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (notification.getStatus() != NotificationStatus.READ) {
            notification.markRead();
            notificationRepository.saveAndFlush(notification);
        }
        return NotificationResponse.from(notification);
    }

    private void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            String property = order.getProperty();
            if (!property.equals("createdAt") && !property.equals("sentAt") && !property.equals("status")) {
                throw new JobPilotException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported notification sort property: " + property
                );
            }
        }
    }
}
