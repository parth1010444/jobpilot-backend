package com.jobpilot.jobanalysis;

import com.jobpilot.auth.security.UserPrincipal;
import com.jobpilot.jobanalysis.dto.JobMatchResponse;
import com.jobpilot.jobanalysis.dto.PreviewJobAnalysisRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JobAnalysisController {

    private final JobAnalysisService jobAnalysisService;

    public JobAnalysisController(JobAnalysisService jobAnalysisService) {
        this.jobAnalysisService = jobAnalysisService;
    }

    @PostMapping("/api/applications/{applicationId}/analyze")
    public JobMatchResponse analyze(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID applicationId
    ) {
        return jobAnalysisService.analyzeApplication(principal.getId(), applicationId);
    }

    @GetMapping("/api/applications/{applicationId}/match")
    public JobMatchResponse match(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID applicationId
    ) {
        return jobAnalysisService.matchApplication(principal.getId(), applicationId);
    }

    @PostMapping("/api/job-analysis/preview")
    public JobMatchResponse preview(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PreviewJobAnalysisRequest request
    ) {
        return jobAnalysisService.preview(principal.getId(), request.jobDescription());
    }
}
