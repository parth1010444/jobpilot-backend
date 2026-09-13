package com.jobpilot.interview;

import com.jobpilot.application.Application;
import com.jobpilot.application.ApplicationRepository;
import com.jobpilot.common.error.JobPilotException;
import com.jobpilot.infrastructure.cache.CacheEviction;
import com.jobpilot.interview.dto.CreateInterviewRequest;
import com.jobpilot.interview.dto.InterviewResponse;
import com.jobpilot.interview.dto.UpdateInterviewRequest;
import com.jobpilot.interview.validator.InterviewStatusTransitionValidator;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "roundNumber", "scheduledAt", "createdAt", "updatedAt", "status", "type"
    );

    private final InterviewRepository interviewRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewStatusTransitionValidator transitionValidator;
    private final CacheEviction cacheEviction;

    public InterviewService(
            InterviewRepository interviewRepository,
            ApplicationRepository applicationRepository,
            InterviewStatusTransitionValidator transitionValidator,
            CacheEviction cacheEviction
    ) {
        this.interviewRepository = interviewRepository;
        this.applicationRepository = applicationRepository;
        this.transitionValidator = transitionValidator;
        this.cacheEviction = cacheEviction;
    }

    @Transactional
    public InterviewResponse create(UUID userId, UUID applicationId, CreateInterviewRequest request) {
        Application application = requireOwnedApplication(userId, applicationId);
        if (interviewRepository.existsByApplicationIdAndRoundNumber(applicationId, request.roundNumber())) {
            throw duplicateRound(request.roundNumber());
        }

        InterviewStatus status = request.status() != null ? request.status() : InterviewStatus.SCHEDULED;
        Interview interview = new Interview(
                application,
                request.roundNumber(),
                request.type(),
                status,
                request.scheduledAt(),
                blankToNull(request.interviewer()),
                blankToNull(request.meetingLink()),
                blankToNull(request.notes()),
                blankToNull(request.feedback())
        );
        try {
            interviewRepository.saveAndFlush(interview);
        } catch (DataIntegrityViolationException ex) {
            throw new JobPilotException(HttpStatus.CONFLICT, "Round number already exists for this application", ex);
        }
        cacheEviction.evictUserRecommendations(userId);
        return InterviewResponse.from(interview);
    }

    @Transactional(readOnly = true)
    public Page<InterviewResponse> list(UUID userId, UUID applicationId, Pageable pageable) {
        requireOwnedApplication(userId, applicationId);
        validateSort(pageable.getSort());
        return interviewRepository.findByApplicationId(applicationId, pageable).map(InterviewResponse::from);
    }

    @Transactional(readOnly = true)
    public InterviewResponse get(UUID userId, UUID id) {
        return InterviewResponse.from(requireOwnedInterview(userId, id));
    }

    @Transactional
    public InterviewResponse update(UUID userId, UUID id, UpdateInterviewRequest request) {
        Interview interview = requireOwnedInterview(userId, id);
        if (request.version() != null && !interview.getVersion().equals(request.version())) {
            throw new JobPilotException(
                    HttpStatus.CONFLICT,
                    "Interview was modified by another request; refresh and retry"
            );
        }

        if (request.roundNumber() != null && request.roundNumber() != interview.getRoundNumber()) {
            UUID applicationId = interview.getApplication().getId();
            if (interviewRepository.existsByApplicationIdAndRoundNumberAndIdNot(
                    applicationId, request.roundNumber(), interview.getId())) {
                throw duplicateRound(request.roundNumber());
            }
            interview.setRoundNumber(request.roundNumber());
        }
        if (request.type() != null) {
            interview.setType(request.type());
        }
        if (request.status() != null) {
            transitionValidator.validate(interview.getStatus(), request.status());
            interview.setStatus(request.status());
        }
        if (request.scheduledAt() != null) {
            interview.setScheduledAt(request.scheduledAt());
        }
        if (request.interviewer() != null) {
            interview.setInterviewer(blankToNull(request.interviewer()));
        }
        if (request.meetingLink() != null) {
            interview.setMeetingLink(blankToNull(request.meetingLink()));
        }
        if (request.notes() != null) {
            interview.setNotes(blankToNull(request.notes()));
        }
        if (request.feedback() != null) {
            interview.setFeedback(blankToNull(request.feedback()));
        }

        try {
            interviewRepository.saveAndFlush(interview);
        } catch (DataIntegrityViolationException ex) {
            throw new JobPilotException(HttpStatus.CONFLICT, "Round number already exists for this application", ex);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new JobPilotException(
                    HttpStatus.CONFLICT,
                    "Interview was modified by another request; refresh and retry",
                    ex
            );
        }
        cacheEviction.evictUserRecommendations(userId);
        return InterviewResponse.from(interview);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        interviewRepository.delete(requireOwnedInterview(userId, id));
        cacheEviction.evictUserRecommendations(userId);
    }

    private Application requireOwnedApplication(UUID userId, UUID applicationId) {
        return applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new JobPilotException(HttpStatus.NOT_FOUND, "Application not found"));
    }

    private Interview requireOwnedInterview(UUID userId, UUID id) {
        return interviewRepository.findByIdAndApplicationUserId(id, userId)
                .orElseThrow(() -> new JobPilotException(HttpStatus.NOT_FOUND, "Interview not found"));
    }

    private JobPilotException duplicateRound(int roundNumber) {
        return new JobPilotException(
                HttpStatus.CONFLICT,
                "Round number " + roundNumber + " already exists for this application"
        );
    }

    private void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new JobPilotException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported interview sort property: " + order.getProperty()
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
