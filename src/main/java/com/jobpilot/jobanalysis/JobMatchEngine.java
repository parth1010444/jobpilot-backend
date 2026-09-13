package com.jobpilot.jobanalysis;

import com.jobpilot.jobanalysis.dto.JobMatchResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Deterministic, explainable skill match scorer.
 *
 * <p>Formula:
 * <ul>
 *   <li>If {@code requiredSkills} is empty → score {@code 100}, all lists empty.</li>
 *   <li>Otherwise {@code score = round(100.0 * matched.size() / max(required.size(), 1))}.</li>
 * </ul>
 * This is overlap coverage, not a hiring probability.
 */
@Component
public class JobMatchEngine {

    public JobMatchResponse match(Collection<String> requiredSkills, Collection<String> userSkills) {
        List<String> required = normalizeUniqueSorted(requiredSkills);
        if (required.isEmpty()) {
            return new JobMatchResponse(100, List.of(), List.of(), List.of());
        }

        Set<String> userSet = userSkills == null
                ? Set.of()
                : userSkills.stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(s -> s.trim().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String skill : required) {
            if (userSet.contains(skill)) {
                matched.add(skill);
            } else {
                missing.add(skill);
            }
        }

        int score = (int) Math.round(100.0 * matched.size() / Math.max(required.size(), 1));
        return new JobMatchResponse(
                score,
                List.copyOf(matched),
                List.copyOf(missing),
                List.copyOf(required)
        );
    }

    private static List<String> normalizeUniqueSorted(Collection<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String skill : skills) {
            if (skill == null || skill.isBlank()) {
                continue;
            }
            unique.add(skill.trim().toLowerCase(Locale.ROOT));
        }
        List<String> sorted = new ArrayList<>(unique);
        sorted.sort(String::compareTo);
        return sorted;
    }
}
