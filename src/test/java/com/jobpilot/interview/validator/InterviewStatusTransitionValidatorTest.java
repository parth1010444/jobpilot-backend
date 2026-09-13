package com.jobpilot.interview.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobpilot.common.error.JobPilotException;
import com.jobpilot.interview.InterviewStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

class InterviewStatusTransitionValidatorTest {

    private InterviewStatusTransitionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new InterviewStatusTransitionValidator();
    }

    @ParameterizedTest
    @CsvSource({
            "SCHEDULED, COMPLETED",
            "SCHEDULED, CANCELLED",
            "SCHEDULED, NO_SHOW",
            "SCHEDULED, SCHEDULED",
            "COMPLETED, COMPLETED",
            "CANCELLED, CANCELLED",
            "NO_SHOW, NO_SHOW"
    })
    void allowsScheduledToTerminalAndNoChange(InterviewStatus from, InterviewStatus to) {
        assertDoesNotThrow(() -> validator.validate(from, to));
        assertTrue(validator.isAllowed(from, to));
    }

    @ParameterizedTest
    @EnumSource(value = InterviewStatus.class, names = {"COMPLETED", "CANCELLED", "NO_SHOW"})
    void terminalStatusesCannotTransition(InterviewStatus from) {
        JobPilotException ex = assertThrows(
                JobPilotException.class,
                () -> validator.validate(from, InterviewStatus.SCHEDULED)
        );
        assertTrue(ex.getStatus() == HttpStatus.BAD_REQUEST);
        assertTrue(ex.getMessage().contains("Invalid interview status transition"));
        assertFalse(validator.isAllowed(from, InterviewStatus.SCHEDULED));
    }

    @Test
    void nullStatusesRejected() {
        assertThrows(JobPilotException.class, () -> validator.validate(null, InterviewStatus.SCHEDULED));
        assertThrows(JobPilotException.class, () -> validator.validate(InterviewStatus.SCHEDULED, null));
    }
}
