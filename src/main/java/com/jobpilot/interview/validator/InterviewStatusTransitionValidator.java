package com.jobpilot.interview.validator;

import com.jobpilot.common.error.JobPilotException;
import com.jobpilot.interview.InterviewStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class InterviewStatusTransitionValidator {

    private static final Map<InterviewStatus, Set<InterviewStatus>> ALLOWED = new EnumMap<>(InterviewStatus.class);

    static {
        ALLOWED.put(InterviewStatus.SCHEDULED, EnumSet.of(
                InterviewStatus.COMPLETED,
                InterviewStatus.CANCELLED,
                InterviewStatus.NO_SHOW
        ));
        ALLOWED.put(InterviewStatus.COMPLETED, EnumSet.noneOf(InterviewStatus.class));
        ALLOWED.put(InterviewStatus.CANCELLED, EnumSet.noneOf(InterviewStatus.class));
        ALLOWED.put(InterviewStatus.NO_SHOW, EnumSet.noneOf(InterviewStatus.class));
    }

    public void validate(InterviewStatus from, InterviewStatus to) {
        if (from == null || to == null) {
            throw new JobPilotException(HttpStatus.BAD_REQUEST, "Interview status is required");
        }
        if (from == to) {
            return;
        }
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new JobPilotException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid interview status transition from " + from + " to " + to
            );
        }
    }

    public boolean isAllowed(InterviewStatus from, InterviewStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return from == to || ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
