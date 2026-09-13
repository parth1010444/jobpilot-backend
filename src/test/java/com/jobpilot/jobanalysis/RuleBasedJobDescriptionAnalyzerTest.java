package com.jobpilot.jobanalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RuleBasedJobDescriptionAnalyzerTest {

    private RuleBasedJobDescriptionAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new RuleBasedJobDescriptionAnalyzer(new SkillDictionary());
    }

    @Test
    void extractsKnownSkillsAndAliases() {
        String jd = """
                We need a backend engineer with Java, Spring Boot, and experience with
                Postgres, Redis, Docker, K8s, and AWS. Familiarity with System Design
                and Kafka is a plus.
                """;

        JobAnalysisResult result = analyzer.analyze(jd);
        List<String> skills = result.requiredSkills();

        assertTrue(skills.contains("java"));
        assertTrue(skills.contains("spring boot"));
        assertTrue(skills.contains("postgresql"));
        assertTrue(skills.contains("redis"));
        assertTrue(skills.contains("docker"));
        assertTrue(skills.contains("kubernetes"));
        assertTrue(skills.contains("aws"));
        assertTrue(skills.contains("system design"));
        assertTrue(skills.contains("kafka"));
        assertEquals(skills.stream().sorted().toList(), skills);
    }

    @Test
    void javaDoesNotMatchInsideJavascript() {
        JobAnalysisResult result = analyzer.analyze("Strong JavaScript and TypeScript required.");
        assertFalse(result.requiredSkills().contains("java"));
        assertTrue(result.requiredSkills().contains("javascript"));
        assertTrue(result.requiredSkills().contains("typescript"));
    }

    @Test
    void blankOrNullYieldsEmpty() {
        assertEquals(List.of(), analyzer.analyze(null).requiredSkills());
        assertEquals(List.of(), analyzer.analyze("   ").requiredSkills());
        assertEquals(List.of(), analyzer.analyze("").requiredSkills());
    }

    @Test
    void caseInsensitiveAndDeterministic() {
        JobAnalysisResult a = analyzer.analyze("JAVA and K8S");
        JobAnalysisResult b = analyzer.analyze("java and k8s");
        assertEquals(a.requiredSkills(), b.requiredSkills());
        assertTrue(a.requiredSkills().contains("java"));
        assertTrue(a.requiredSkills().contains("kubernetes"));
    }
}
