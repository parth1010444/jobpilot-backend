package com.jobpilot.recommendation;

import com.jobpilot.auth.security.UserPrincipal;
import com.jobpilot.recommendation.dto.RecommendationResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/api/applications/{id}/recommendation")
    public RecommendationResponse getForApplication(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        return recommendationService.getForApplication(principal.getId(), id);
    }

    @GetMapping("/api/recommendations")
    public List<RecommendationResponse> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Integer limit
    ) {
        return recommendationService.listForUser(principal.getId(), limit);
    }
}
