package com.jobpilot.application.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobpilot.application.ApplicationStatus;
import com.jobpilot.common.error.JobPilotException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

class ApplicationStatusTransitionValidatorTest {

    private ApplicationStatusTransitionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ApplicationStatusTransitionValidator();
    }

    @ParameterizedTest
    @CsvSource({
            "SAVED, APPLIED",
            "SAVED, WITHDRAWN",
            "APPLIED, OA",
            "APPLIED, INTERVIEW",
            "APPLIED, REJECTED",
            "APPLIED, WITHDRAWN",
            "OA, INTERVIEW",
            "OA, REJECTED",
            "OA, WITHDRAWN",
            "INTERVIEW, INTERVIEW",
            "INTERVIEW, OFFER",
            "INTERVIEW, REJECTED",
            "INTERVIEW, WITHDRAWN",
            "OFFER, REJECTED",
            "OFFER, WITHDRAWN",
            "REJECTED, REJECTED",
            "WITHDRAWN, WITHDRAWN"
    })
    void allowsValidTransitions(ApplicationStatus from, ApplicationStatus to) {
        assertDoesNotThrow(() -> validator.validate(from, to));
        assertTrue(validator.isAllowed(from, to));
    }

    @ParameterizedTest
    @CsvSource({
            "SAVED, INTERVIEW",
            "SAVED, OFFER",
            "SAVED, REJECTED",
            "APPLIED, SAVED",
            "APPLIED, OFFER",
            "OA, APPLIED",
            "OA, OFFER",
            "INTERVIEW, SAVED",
            "INTERVIEW, APPLIED",
            "OFFER, INTERVIEW",
            "OFFER, APPLIED",
            "REJECTED, APPLIED",
            "REJECTED, INTERVIEW",
            "WITHDRAWN, SAVED",
            "WITHDRAWN, APPLIED"
    })
    void rejectsInvalidTransitions(ApplicationStatus from, ApplicationStatus to) {
        JobPilotException ex = assertThrows(JobPilotException.class, () -> validator.validate(from, to));
        assertTrue(ex.getStatus() == HttpStatus.BAD_REQUEST);
        assertTrue(ex.getMessage().contains("Invalid status transition"));
        assertFalse(validator.isAllowed(from, to));
    }

    @Test
    void nullStatusesRejected() {
        assertThrows(JobPilotException.class, () -> validator.validate(null, ApplicationStatus.SAVED));
        assertThrows(JobPilotException.class, () -> validator.validate(ApplicationStatus.SAVED, null));
    }
}
