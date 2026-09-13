package com.jobpilot.reminder;

import com.jobpilot.application.ApplicationRepository;
import com.jobpilot.common.error.JobPilotException;
import com.jobpilot.reminder.dto.CreateReminderRequest;
import com.jobpilot.reminder.dto.ReminderResponse;
import com.jobpilot.reminder.dto.UpdateReminderRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "scheduledAt", "createdAt", "status", "type", "title"
    );

    private final ReminderRepository reminderRepository;
    private final ApplicationRepository applicationRepository;
    private final Clock clock;

    public ReminderService(
            ReminderRepository reminderRepository,
            ApplicationRepository applicationRepository,
            Clock clock
    ) {
        this.reminderRepository = reminderRepository;
        this.applicationRepository = applicationRepository;
        this.clock = clock;
    }

    @Transactional
    public ReminderResponse create(UUID userId, CreateReminderRequest request) {
        UUID applicationId = request.applicationId();
        if (request.type() != ReminderType.CUSTOM && applicationId == null) {
            throw new JobPilotException(
                    HttpStatus.BAD_REQUEST,
                    "applicationId is required for reminder type " + request.type()
            );
        }
        if (applicationId != null) {
            requireOwnedApplication(userId, applicationId);
        }

        Reminder reminder = new Reminder(
                userId,
                applicationId,
                request.type(),
                request.title().trim(),
                blankToNull(request.description()),
                request.scheduledAt()
        );
        reminderRepository.saveAndFlush(reminder);
        return ReminderResponse.from(reminder);
    }

    @Transactional(readOnly = true)
    public Page<ReminderResponse> list(UUID userId, ReminderStatus status, Pageable pageable) {
        validateSort(pageable.getSort());
        Page<Reminder> page = status == null
                ? reminderRepository.findByUserId(userId, pageable)
                : reminderRepository.findByUserIdAndStatus(userId, status, pageable);
        return page.map(ReminderResponse::from);
    }

    @Transactional(readOnly = true)
    public ReminderResponse get(UUID userId, UUID id) {
        return ReminderResponse.from(requireOwned(userId, id));
    }

    @Transactional
    public ReminderResponse update(UUID userId, UUID id, UpdateReminderRequest request) {
        Reminder reminder = requireOwned(userId, id);

        if (request.status() == ReminderStatus.CANCELLED) {
            if (reminder.getStatus() != ReminderStatus.PENDING) {
                throw new JobPilotException(
                        HttpStatus.CONFLICT,
                        "Only PENDING reminders can be cancelled"
                );
            }
            reminder.markCancelled(clock.instant());
            reminderRepository.saveAndFlush(reminder);
            return ReminderResponse.from(reminder);
        }

        if (request.status() != null && request.status() != ReminderStatus.PENDING) {
            throw new JobPilotException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported status update: " + request.status() + " (use CANCELLED to cancel)"
            );
        }

        if (reminder.getStatus() != ReminderStatus.PENDING) {
            throw new JobPilotException(
                    HttpStatus.CONFLICT,
                    "Only PENDING reminders can be updated"
            );
        }

        if (request.title() != null) {
            String title = request.title().trim();
            if (title.isEmpty()) {
                throw new JobPilotException(HttpStatus.BAD_REQUEST, "title must not be blank");
            }
            reminder.setTitle(title);
        }
        if (request.description() != null) {
            reminder.setDescription(blankToNull(request.description()));
        }
        if (request.scheduledAt() != null) {
            reminder.setScheduledAt(request.scheduledAt());
        }

        reminderRepository.saveAndFlush(reminder);
        return ReminderResponse.from(reminder);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        reminderRepository.delete(requireOwned(userId, id));
    }

    private Reminder requireOwned(UUID userId, UUID id) {
        return reminderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new JobPilotException(HttpStatus.NOT_FOUND, "Reminder not found"));
    }

    private void requireOwnedApplication(UUID userId, UUID applicationId) {
        if (!applicationRepository.existsByIdAndUserId(applicationId, userId)) {
            throw new JobPilotException(HttpStatus.NOT_FOUND, "Application not found");
        }
    }

    private void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new JobPilotException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported reminder sort property: " + order.getProperty()
                );
            }
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
