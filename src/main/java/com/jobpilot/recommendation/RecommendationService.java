package com.jobpilot.recommendation;

import com.jobpilot.application.Application;
import com.jobpilot.application.ApplicationRepository;
import com.jobpilot.application.ApplicationStatus;
import com.jobpilot.common.error.JobPilotException;
import com.jobpilot.infrastructure.cache.CacheNames;
import com.jobpilot.interview.Interview;
import com.jobpilot.interview.InterviewRepository;
import com.jobpilot.jobanalysis.JobMatchEngine;
import com.jobpilot.jobanalysis.JobRequirement;
import com.jobpilot.jobanalysis.JobRequirementRepository;
import com.jobpilot.recommendation.RecommendationCandidate.InterviewInfo;
import com.jobpilot.recommendation.dto.RecommendationResponse;
import com.jobpilot.recommendation.rules.RecommendationRule.RuleMatch;
import com.jobpilot.skill.Skill;
import com.jobpilot.skill.SkillRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    /** Statuses excluded from the cross-application feed (still 404-owned for single lookup). */
    private static final Set<ApplicationStatus> INACTIVE = Set.of(ApplicationStatus.WITHDRAWN);

    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final JobRequirementRepository jobRequirementRepository;
    private final SkillRepository skillRepository;
    private final JobMatchEngine jobMatchEngine;
    private final RecommendationEngine recommendationEngine;

    public RecommendationService(
            ApplicationRepository applicationRepository,
            InterviewRepository interviewRepository,
            JobRequirementRepository jobRequirementRepository,
            SkillRepository skillRepository,
            JobMatchEngine jobMatchEngine,
            RecommendationEngine recommendationEngine
    ) {
        this.applicationRepository = applicationRepository;
        this.interviewRepository = interviewRepository;
        this.jobRequirementRepository = jobRequirementRepository;
        this.skillRepository = skillRepository;
        this.jobMatchEngine = jobMatchEngine;
        this.recommendationEngine = recommendationEngine;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse getForApplication(UUID userId, UUID applicationId) {
        Application application = applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new JobPilotException(HttpStatus.NOT_FOUND, "Application not found"));

        RecommendationCandidate candidate = toCandidate(application, userId);
        return recommendationEngine.recommend(candidate)
                .orElseThrow(() -> new JobPilotException(
                        HttpStatus.NOT_FOUND,
                        "No recommendation available for this application"
                ));
    }

    @Cacheable(
            cacheNames = CacheNames.RECOMMENDATIONS,
            key = "T(com.jobpilot.infrastructure.cache.CacheNames).recommendationsKey(#userId, #limit)"
    )
    @Transactional(readOnly = true)
    public List<RecommendationResponse> listForUser(UUID userId, Integer limit) {
        int effectiveLimit = normalizeLimit(limit);
        List<Application> applications = applicationRepository.findByUserId(userId).stream()
                .filter(a -> !INACTIVE.contains(a.getStatus()))
                .toList();

        if (applications.isEmpty()) {
            return List.of();
        }

        List<String> userSkills = skillRepository.findByUserIdOrderByNameAsc(userId).stream()
                .map(Skill::getName)
                .toList();

        List<UUID> ids = applications.stream().map(Application::getId).toList();
        Map<UUID, List<Interview>> interviewsByApp = interviewRepository.findByApplicationIdIn(ids).stream()
                .collect(Collectors.groupingBy(i -> i.getApplication().getId()));

        Map<UUID, List<String>> requirementsByApp = loadRequirements(ids);

        List<RuleMatch> scored = new ArrayList<>();
        for (Application application : applications) {
            UUID appId = application.getId();
            List<InterviewInfo> interviews = interviewsByApp.getOrDefault(appId, List.of()).stream()
                    .map(i -> new InterviewInfo(i.getStatus(), i.getScheduledAt(), i.getUpdatedAt()))
                    .toList();
            Integer matchScore = matchScoreOrNull(requirementsByApp.get(appId), userSkills);
            RecommendationCandidate candidate = new RecommendationCandidate(
                    appId,
                    application.getStatus(),
                    application.getUpdatedAt(),
                    application.getCreatedAt(),
                    interviews,
                    matchScore
            );
            recommendationEngine.recommendScored(candidate).ifPresent(scored::add);
        }

        return scored.stream()
                .sorted(RecommendationEngine.BEST_FIRST)
                .limit(effectiveLimit)
                .map(RuleMatch::recommendation)
                .toList();
    }

    private RecommendationCandidate toCandidate(Application application, UUID userId) {
        List<InterviewInfo> interviews = interviewRepository.findByApplicationId(application.getId()).stream()
                .map(i -> new InterviewInfo(i.getStatus(), i.getScheduledAt(), i.getUpdatedAt()))
                .toList();

        Integer matchScore = null;
        if (jobRequirementRepository.existsByApplicationId(application.getId())) {
            List<String> required = jobRequirementRepository
                    .findByApplicationIdOrderBySkillNameAsc(application.getId()).stream()
                    .map(JobRequirement::getSkillName)
                    .toList();
            List<String> userSkills = skillRepository.findByUserIdOrderByNameAsc(userId).stream()
                    .map(Skill::getName)
                    .toList();
            matchScore = jobMatchEngine.match(required, userSkills).score();
        }

        return new RecommendationCandidate(
                application.getId(),
                application.getStatus(),
                application.getUpdatedAt(),
                application.getCreatedAt(),
                interviews,
                matchScore
        );
    }

    private Map<UUID, List<String>> loadRequirements(List<UUID> applicationIds) {
        if (applicationIds.isEmpty()) {
            return Map.of();
        }
        return jobRequirementRepository.findByApplicationIdIn(applicationIds).stream()
                .collect(Collectors.groupingBy(
                        JobRequirement::getApplicationId,
                        Collectors.mapping(JobRequirement::getSkillName, Collectors.toList())
                ));
    }

    private Integer matchScoreOrNull(List<String> required, List<String> userSkills) {
        if (required == null || required.isEmpty()) {
            return null;
        }
        return jobMatchEngine.match(required, userSkills).score();
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            throw new JobPilotException(HttpStatus.BAD_REQUEST, "limit must be at least 1");
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
