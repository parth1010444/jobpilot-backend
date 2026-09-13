package com.jobpilot.application.validator;

import com.jobpilot.application.ApplicationStatus;
import com.jobpilot.common.error.JobPilotException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStatusTransitionValidator {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED = new EnumMap<>(ApplicationStatus.class);

    static {
        ALLOWED.put(ApplicationStatus.SAVED, EnumSet.of(
                ApplicationStatus.APPLIED,
                ApplicationStatus.WITHDRAWN
        ));
        ALLOWED.put(ApplicationStatus.APPLIED, EnumSet.of(
                ApplicationStatus.OA,
                ApplicationStatus.INTERVIEW,
                ApplicationStatus.REJECTED,
                ApplicationStatus.WITHDRAWN
        ));
        ALLOWED.put(ApplicationStatus.OA, EnumSet.of(
                ApplicationStatus.INTERVIEW,
                ApplicationStatus.REJECTED,
                ApplicationStatus.WITHDRAWN
        ));
        ALLOWED.put(ApplicationStatus.INTERVIEW, EnumSet.of(
                ApplicationStatus.INTERVIEW,
                ApplicationStatus.OFFER,
                ApplicationStatus.REJECTED,
                ApplicationStatus.WITHDRAWN
        ));
        ALLOWED.put(ApplicationStatus.OFFER, EnumSet.of(
                ApplicationStatus.REJECTED,
                ApplicationStatus.WITHDRAWN
        ));
        ALLOWED.put(ApplicationStatus.REJECTED, EnumSet.noneOf(ApplicationStatus.class));
        ALLOWED.put(ApplicationStatus.WITHDRAWN, EnumSet.noneOf(ApplicationStatus.class));
    }

    /**
     * No-op when statuses are equal (except for terminal states which are still "same").
     * Same status is always allowed as a no-change. Transitions are validated only when different.
     */
    public void validate(ApplicationStatus from, ApplicationStatus to) {
        if (from == null || to == null) {
            throw new JobPilotException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        if (from == to) {
            return;
        }
        Set<ApplicationStatus> allowed = ALLOWED.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new JobPilotException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid status transition from " + from + " to " + to
            );
        }
    }

    public boolean isAllowed(ApplicationStatus from, ApplicationStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
