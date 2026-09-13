package com.jobpilot.infrastructure.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobpilot.cache")
public class JobPilotCacheProperties {

    /**
     * TTL for {@link CacheNames#RECOMMENDATIONS}. Applied when Redis is the
     * cache store. In-memory (test / Redis-disabled) has no TTL.
     */
    private Duration recommendationsTtl = Duration.ofSeconds(120);

    /**
     * TTL for {@link CacheNames#APPLICATION_MATCH}.
     */
    private Duration applicationMatchTtl = Duration.ofSeconds(180);

    /**
     * TTL for {@link CacheNames#ANALYTICS} (summary / funnel).
     */
    private Duration analyticsTtl = Duration.ofSeconds(60);

    public Duration getRecommendationsTtl() {
        return recommendationsTtl;
    }

    public void setRecommendationsTtl(Duration recommendationsTtl) {
        this.recommendationsTtl = recommendationsTtl;
    }

    public Duration getApplicationMatchTtl() {
        return applicationMatchTtl;
    }

    public void setApplicationMatchTtl(Duration applicationMatchTtl) {
        this.applicationMatchTtl = applicationMatchTtl;
    }

    public Duration getAnalyticsTtl() {
        return analyticsTtl;
    }

    public void setAnalyticsTtl(Duration analyticsTtl) {
        this.analyticsTtl = analyticsTtl;
    }
}
