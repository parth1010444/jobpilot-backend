package com.jobpilot.jobanalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jobpilot.jobanalysis.dto.JobMatchResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobMatchEngineTest {

    private JobMatchEngine engine;

    @BeforeEach
    void setUp() {
        engine = new JobMatchEngine();
    }

    @Test
    void scoreIsRoundHundredTimesMatchedOverRequired() {
        // 5 of 7 → round(100 * 5/7) = round(71.428...) = 71
        JobMatchResponse response = engine.match(
                List.of("java", "spring boot", "kafka", "postgresql", "redis", "docker", "kubernetes"),
                List.of("java", "kafka", "redis", "docker", "kubernetes")
        );

        assertEquals(71, response.score());
        assertEquals(
                List.of("docker", "java", "kafka", "kubernetes", "redis"),
                response.matchedSkills()
        );
        assertEquals(List.of("postgresql", "spring boot"), response.missingSkills());
        assertEquals(
                List.of("docker", "java", "kafka", "kubernetes", "postgresql", "redis", "spring boot"),
                response.requiredSkills()
        );
    }

    @Test
    void emptyRequiredYieldsScore100AndEmptyLists() {
        JobMatchResponse response = engine.match(List.of(), List.of("java"));
        assertEquals(100, response.score());
        assertEquals(List.of(), response.matchedSkills());
        assertEquals(List.of(), response.missingSkills());
        assertEquals(List.of(), response.requiredSkills());
    }

    @Test
    void fullMatchIs100() {
        JobMatchResponse response = engine.match(List.of("java", "kafka"), List.of("JAVA", "Kafka", "redis"));
        assertEquals(100, response.score());
        assertEquals(List.of("java", "kafka"), response.matchedSkills());
        assertEquals(List.of(), response.missingSkills());
    }

    @Test
    void zeroMatchIs0() {
        JobMatchResponse response = engine.match(List.of("java"), List.of("python"));
        assertEquals(0, response.score());
        assertEquals(List.of(), response.matchedSkills());
        assertEquals(List.of("java"), response.missingSkills());
    }
}
