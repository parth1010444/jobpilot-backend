package com.jobpilot.jobanalysis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic keyword/alias matcher against {@link SkillDictionary}.
 * No external LLM. Matching is case-insensitive and uses alphanumeric boundaries
 * so e.g. {@code java} does not match inside {@code javascript}.
 */
@Component
public class RuleBasedJobDescriptionAnalyzer implements JobDescriptionAnalyzer {

    private final List<SkillPattern> patterns;

    public RuleBasedJobDescriptionAnalyzer(SkillDictionary skillDictionary) {
        this.patterns = buildPatterns(skillDictionary);
    }

    @Override
    public JobAnalysisResult analyze(String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) {
            return new JobAnalysisResult(List.of());
        }

        String haystack = jobDescription.toLowerCase(Locale.ROOT);
        Set<String> found = new LinkedHashSet<>();
        for (SkillPattern pattern : patterns) {
            if (pattern.pattern().matcher(haystack).find()) {
                found.add(pattern.canonical());
            }
        }

        List<String> required = new ArrayList<>(found);
        required.sort(String::compareTo);
        return new JobAnalysisResult(List.copyOf(required));
    }

    private static List<SkillPattern> buildPatterns(SkillDictionary dictionary) {
        List<SkillPattern> list = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : dictionary.entries().entrySet()) {
            String canonical = entry.getKey();
            for (String alias : entry.getValue()) {
                list.add(new SkillPattern(canonical, compileAlias(alias), alias.length()));
            }
        }
        // Longer aliases first (stable overlap handling; each hit still maps to its canonical).
        list.sort((a, b) -> Integer.compare(b.aliasLength(), a.aliasLength()));
        return List.copyOf(list);
    }

    /**
     * Match alias with boundaries that treat non-alphanumeric as separators,
     * while still allowing punctuation inside the alias (c++, c#, node.js, ci/cd).
     */
    static Pattern compileAlias(String aliasLower) {
        String escaped = Pattern.quote(aliasLower);
        return Pattern.compile("(?<![\\p{Alnum}])" + escaped + "(?![\\p{Alnum}])");
    }

    private record SkillPattern(String canonical, Pattern pattern, int aliasLength) {
    }
}
