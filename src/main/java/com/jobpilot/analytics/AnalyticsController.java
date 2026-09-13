package com.jobpilot.analytics;

import com.jobpilot.analytics.dto.AnalyticsFunnelResponse;
import com.jobpilot.analytics.dto.AnalyticsSummaryResponse;
import com.jobpilot.analytics.dto.AnalyticsTimelineResponse;
import com.jobpilot.analytics.dto.SkillsGapResponse;
import com.jobpilot.analytics.dto.TimelineBucket;
import com.jobpilot.auth.security.UserPrincipal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public AnalyticsSummaryResponse summary(@AuthenticationPrincipal UserPrincipal principal) {
        return analyticsService.summary(principal.getId());
    }

    @GetMapping("/funnel")
    public AnalyticsFunnelResponse funnel(@AuthenticationPrincipal UserPrincipal principal) {
        return analyticsService.funnel(principal.getId());
    }

    @GetMapping("/timeline")
    public AnalyticsTimelineResponse timeline(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false, defaultValue = "WEEK") TimelineBucket bucket,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return analyticsService.timeline(principal.getId(), bucket, from, to);
    }

    @GetMapping("/skills-gap")
    public SkillsGapResponse skillsGap(@AuthenticationPrincipal UserPrincipal principal) {
        return analyticsService.skillsGap(principal.getId());
    }
}
