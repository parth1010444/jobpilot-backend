package com.jobpilot.application;

import com.jobpilot.application.dto.ApplicationResponse;
import com.jobpilot.application.dto.CreateApplicationRequest;
import com.jobpilot.application.dto.UpdateApplicationRequest;
import com.jobpilot.application.validator.ApplicationStatusTransitionValidator;
import com.jobpilot.common.error.JobPilotException;
import com.jobpilot.event.OutboxPublisher;
import com.jobpilot.event.dto.ApplicationStatusChangedEvent;
import com.jobpilot.resume.ResumeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "appliedAt",
            "createdAt",
            "updatedAt",
            "company",
            "jobTitle",
            "status"
    );

    private final ApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final ApplicationStatusTransitionValidator transitionValidator;
    private final ResumeRepository resumeRepository;
    private final OutboxPublisher outboxPublisher;
    private final Clock clock;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            ApplicationStatusHistoryRepository historyRepository,
            ApplicationStatusTransitionValidator transitionValidator,
            ResumeRepository resumeRepository,
            OutboxPublisher outboxPublisher,
            Clock clock
    ) {
        this.applicationRepository = applicationRepository;
        this.historyRepository = historyRepository;
        this.transitionValidator = transitionValidator;
        this.resumeRepository = resumeRepository;
        this.outboxPublisher = outboxPublisher;
        this.clock = clock;
    }

    @Transactional
    public ApplicationResponse create(UUID userId, CreateApplicationRequest request) {
        ApplicationStatus status = request.status() != null ? request.status() : ApplicationStatus.SAVED;
        Instant appliedAt = request.appliedAt();
        if (appliedAt == null && status == ApplicationStatus.APPLIED) {
            appliedAt = clock.instant();
        }

        Application application = new Application(
                userId,
                request.company().trim(),
                request.jobTitle().trim(),
                blankToNull(request.jobUrl()),
                blankToNull(request.location()),
                request.employmentType(),
                request.source(),
                status,
                request.salaryMin(),
                request.salaryMax(),
                blankToNull(request.jobDescription()),
                blankToNull(request.notes()),
                appliedAt,
                resolveResumeId(userId, request.resumeId())
        );

        applicationRepository.save(application);
        recordHistory(application.getId(), null, status);
        publishStatusChanged(application, null, status);
        return ApplicationResponse.from(application);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> list(UUID userId, ApplicationStatus status, String q, Pageable pageable) {
        validateSort(pageable.getSort());
        Specification<Application> spec = ApplicationSpecifications.withFilters(userId, status, q);
        return applicationRepository.findAll(spec, pageable).map(ApplicationResponse::from);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(UUID userId, UUID id) {
        return ApplicationResponse.from(requireOwned(userId, id));
    }

    @Transactional
    public ApplicationResponse update(UUID userId, UUID id, UpdateApplicationRequest request) {
        Application application = requireOwned(userId, id);

        if (!application.getVersion().equals(request.version())) {
            throw new JobPilotException(
                    HttpStatus.CONFLICT,
                    "Application was modified by another request; refresh and retry"
            );
        }

        if (request.company() != null) {
            if (request.company().isBlank()) {
                throw new JobPilotException(HttpStatus.BAD_REQUEST, "company: must not be blank");
            }
            application.setCompany(request.company().trim());
        }
        if (request.jobTitle() != null) {
            if (request.jobTitle().isBlank()) {
                throw new JobPilotException(HttpStatus.BAD_REQUEST, "jobTitle: must not be blank");
            }
            application.setJobTitle(request.jobTitle().trim());
        }
        if (request.jobUrl() != null) {
            application.setJobUrl(blankToNull(request.jobUrl()));
        }
        if (request.location() != null) {
            application.setLocation(blankToNull(request.location()));
        }
        if (request.employmentType() != null) {
            application.setEmploymentType(request.employmentType());
        }
        if (request.source() != null) {
            application.setSource(request.source());
        }
        if (request.salaryMin() != null) {
            application.setSalaryMin(request.salaryMin());
        }
        if (request.salaryMax() != null) {
            application.setSalaryMax(request.salaryMax());
        }
        if (request.jobDescription() != null) {
            application.setJobDescription(blankToNull(request.jobDescription()));
        }
        if (request.notes() != null) {
            application.setNotes(blankToNull(request.notes()));
        }
        if (request.appliedAt() != null) {
            application.setAppliedAt(request.appliedAt());
        }
        if (request.resumeId() != null) {
            application.setResumeId(resolveResumeId(userId, request.resumeId().orElse(null)));
        }

        if (request.status() != null) {
            ApplicationStatus oldStatus = application.getStatus();
            ApplicationStatus newStatus = request.status();
            transitionValidator.validate(oldStatus, newStatus);
            if (oldStatus != newStatus) {
                application.setStatus(newStatus);
                if (newStatus == ApplicationStatus.APPLIED && application.getAppliedAt() == null) {
                    application.setAppliedAt(clock.instant());
                }
                recordHistory(application.getId(), oldStatus, newStatus);
                publishStatusChanged(application, oldStatus, newStatus);
            }
        }

        try {
            applicationRepository.saveAndFlush(application);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new JobPilotException(
                    HttpStatus.CONFLICT,
                    "Application was modified by another request; refresh and retry",
                    ex
            );
        }

        return ApplicationResponse.from(application);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Application application = requireOwned(userId, id);
        historyRepository.deleteByApplicationId(application.getId());
        applicationRepository.delete(application);
    }

    private void publishStatusChanged(
            Application application,
            ApplicationStatus fromStatus,
            ApplicationStatus toStatus
    ) {
        outboxPublisher.publishApplicationStatusChanged(new ApplicationStatusChangedEvent(
                application.getId(),
                application.getUserId(),
                fromStatus != null ? fromStatus.name() : null,
                toStatus.name(),
                clock.instant()
        ));
    }

    private Application requireOwned(UUID userId, UUID id) {
        return applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new JobPilotException(HttpStatus.NOT_FOUND, "Application not found"));
    }

    /**
     * Resume must belong to the same user. Missing or foreign resume → 404
     * so we do not leak another user's resume ids.
     */
    private UUID resolveResumeId(UUID userId, UUID resumeId) {
        if (resumeId == null) {
            return null;
        }
        if (!resumeRepository.existsByIdAndUserId(resumeId, userId)) {
            throw new JobPilotException(HttpStatus.NOT_FOUND, "Resume not found");
        }
        return resumeId;
    }

    private void recordHistory(UUID applicationId, ApplicationStatus oldStatus, ApplicationStatus newStatus) {
        historyRepository.save(new ApplicationStatusHistory(applicationId, oldStatus, newStatus));
    }

    private void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new JobPilotException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported sort property: " + order.getProperty()
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
