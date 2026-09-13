package com.jobpilot.jobanalysis;

import com.jobpilot.application.Application;
import com.jobpilot.application.ApplicationRepository;
import com.jobpilot.common.error.JobPilotException;
import com.jobpilot.jobanalysis.dto.JobMatchResponse;
import com.jobpilot.skill.Skill;
import com.jobpilot.skill.SkillRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobAnalysisService {

    private final ApplicationRepository applicationRepository;
    private final SkillRepository skillRepository;
    private final JobRequirementRepository jobRequirementRepository;
    private final JobDescriptionAnalyzer jobDescriptionAnalyzer;
    private final JobMatchEngine jobMatchEngine;

    public JobAnalysisService(
            ApplicationRepository applicationRepository,
            SkillRepository skillRepository,
            JobRequirementRepository jobRequirementRepository,
            JobDescriptionAnalyzer jobDescriptionAnalyzer,
            JobMatchEngine jobMatchEngine
    ) {
        this.applicationRepository = applicationRepository;
        this.skillRepository = skillRepository;
        this.jobRequirementRepository = jobRequirementRepository;
        this.jobDescriptionAnalyzer = jobDescriptionAnalyzer;
        this.jobMatchEngine = jobMatchEngine;
    }

    /**
     * Analyze the application's job description, replace stored requirements, and match
     * against the current user's skills.
     */
    @Transactional
    public JobMatchResponse analyzeApplication(UUID userId, UUID applicationId) {
        Application application = requireOwnedApplication(userId, applicationId);
        String jd = application.getJobDescription();
        if (jd == null || jd.isBlank()) {
            throw new JobPilotException(HttpStatus.BAD_REQUEST, "jobDescription is blank");
        }

        JobAnalysisResult analysis = jobDescriptionAnalyzer.analyze(jd);
        replaceRequirements(applicationId, analysis.requiredSkills());
        return jobMatchEngine.match(analysis.requiredSkills(), currentUserSkillNames(userId));
    }

    /**
     * Recompute match from stored requirements, or re-analyze (and persist) if none exist.
     */
    @Transactional
    public JobMatchResponse matchApplication(UUID userId, UUID applicationId) {
        Application application = requireOwnedApplication(userId, applicationId);

        List<String> required;
        if (jobRequirementRepository.existsByApplicationId(applicationId)) {
            required = jobRequirementRepository.findByApplicationIdOrderBySkillNameAsc(applicationId).stream()
                    .map(JobRequirement::getSkillName)
                    .toList();
        } else {
            String jd = application.getJobDescription();
            if (jd == null || jd.isBlank()) {
                throw new JobPilotException(HttpStatus.BAD_REQUEST, "jobDescription is blank");
            }
            JobAnalysisResult analysis = jobDescriptionAnalyzer.analyze(jd);
            replaceRequirements(applicationId, analysis.requiredSkills());
            required = analysis.requiredSkills();
        }

        return jobMatchEngine.match(required, currentUserSkillNames(userId));
    }

    /**
     * Analyze + match without persisting (demo / preview).
     */
    @Transactional(readOnly = true)
    public JobMatchResponse preview(UUID userId, String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) {
            throw new JobPilotException(HttpStatus.BAD_REQUEST, "jobDescription is blank");
        }
        JobAnalysisResult analysis = jobDescriptionAnalyzer.analyze(jobDescription);
        return jobMatchEngine.match(analysis.requiredSkills(), currentUserSkillNames(userId));
    }

    private void replaceRequirements(UUID applicationId, List<String> skillNames) {
        jobRequirementRepository.deleteByApplicationId(applicationId);
        jobRequirementRepository.flush();
        for (String skillName : skillNames) {
            jobRequirementRepository.save(new JobRequirement(applicationId, skillName));
        }
    }

    private List<String> currentUserSkillNames(UUID userId) {
        return skillRepository.findByUserIdOrderByNameAsc(userId).stream()
                .map(Skill::getName)
                .toList();
    }

    private Application requireOwnedApplication(UUID userId, UUID applicationId) {
        return applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new JobPilotException(HttpStatus.NOT_FOUND, "Application not found"));
    }
}
