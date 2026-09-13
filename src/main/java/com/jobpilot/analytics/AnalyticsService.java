package com.jobpilot.analytics;

import com.jobpilot.analytics.dto.AnalyticsFunnelResponse;
import com.jobpilot.analytics.dto.AnalyticsSummaryResponse;
import com.jobpilot.analytics.dto.AnalyticsTimelineResponse;
import com.jobpilot.analytics.dto.FunnelStage;
import com.jobpilot.analytics.dto.SkillGapItem;
import com.jobpilot.analytics.dto.SkillsGapResponse;
import com.jobpilot.analytics.dto.StatusCount;
import com.jobpilot.analytics.dto.TimelineBucket;
import com.jobpilot.analytics.dto.TimelinePoint;
import com.jobpilot.application.Application;
import com.jobpilot.application.ApplicationStatus;
import com.jobpilot.common.error.JobPilotException;
import com.jobpilot.infrastructure.cache.CacheNames;
import com.jobpilot.jobanalysis.JobMatchEngine;
import com.jobpilot.jobanalysis.dto.JobMatchResponse;
import com.jobpilot.jobanalysis.JobRequirement;
import com.jobpilot.jobanalysis.JobRequirementRepository;
import com.jobpilot.skill.Skill;
import com.jobpilot.skill.SkillRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    /** Funnel main path (ordered). */
    private static final List<ApplicationStatus> FUNNEL_STAGES = List.of(
            ApplicationStatus.SAVED,
            ApplicationStatus.APPLIED,
            ApplicationStatus.OA,
            ApplicationStatus.INTERVIEW,
            ApplicationStatus.OFFER
    );

    private static final EnumSet<ApplicationStatus> INACTIVE = EnumSet.of(
            ApplicationStatus.REJECTED,
            ApplicationStatus.WITHDRAWN
    );

    private static final int DEFAULT_SKILLS_GAP_LIMIT = 20;
    private static final int DEFAULT_TIMELINE_WEEKS = 12;

    private final AnalyticsRepository analyticsRepository;
    private final JobRequirementRepository jobRequirementRepository;
    private final SkillRepository skillRepository;
    private final JobMatchEngine jobMatchEngine;
    private final Clock clock;

    public AnalyticsService(
            AnalyticsRepository analyticsRepository,
            JobRequirementRepository jobRequirementRepository,
            SkillRepository skillRepository,
            JobMatchEngine jobMatchEngine,
            Clock clock
    ) {
        this.analyticsRepository = analyticsRepository;
        this.jobRequirementRepository = jobRequirementRepository;
        this.skillRepository = skillRepository;
        this.jobMatchEngine = jobMatchEngine;
        this.clock = clock;
    }

    @Cacheable(cacheNames = CacheNames.ANALYTICS, key = "#userId + ':summary'")
    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(UUID userId) {
        Map<ApplicationStatus, Long> byStatus = statusCounts(userId);

        List<StatusCount> countsByStatus = new ArrayList<>();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            long count = byStatus.getOrDefault(status, 0L);
            if (count > 0) {
                countsByStatus.add(new StatusCount(status, count));
            }
        }

        long total = analyticsRepository.countByUserId(userId);
        long interviewCount = analyticsRepository.countInterviewsByUserId(userId);
        long offerCount = byStatus.getOrDefault(ApplicationStatus.OFFER, 0L);
        long rejectedCount = byStatus.getOrDefault(ApplicationStatus.REJECTED, 0L);
        long active = analyticsRepository.countByUserIdAndStatusNotIn(userId, List.copyOf(INACTIVE));
        Double averageMatchScore = computeAverageMatchScore(userId);

        return new AnalyticsSummaryResponse(
                total,
                List.copyOf(countsByStatus),
                interviewCount,
                offerCount,
                rejectedCount,
                averageMatchScore,
                active
        );
    }

    @Cacheable(cacheNames = CacheNames.ANALYTICS, key = "#userId + ':funnel'")
    @Transactional(readOnly = true)
    public AnalyticsFunnelResponse funnel(UUID userId) {
        Map<ApplicationStatus, Long> byStatus = statusCounts(userId);

        List<FunnelStage> stages = new ArrayList<>();
        Long previous = null;
        for (ApplicationStatus status : FUNNEL_STAGES) {
            long count = byStatus.getOrDefault(status, 0L);
            Double conversion = null;
            if (previous != null && previous > 0) {
                conversion = roundRate((double) count / previous);
            }
            stages.add(new FunnelStage(status, count, conversion));
            previous = count;
        }

        return new AnalyticsFunnelResponse(
                List.copyOf(stages),
                byStatus.getOrDefault(ApplicationStatus.REJECTED, 0L),
                byStatus.getOrDefault(ApplicationStatus.WITHDRAWN, 0L)
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsTimelineResponse timeline(
            UUID userId,
            TimelineBucket bucket,
            LocalDate from,
            LocalDate to
    ) {
        TimelineBucket effectiveBucket = bucket != null ? bucket : TimelineBucket.WEEK;
        LocalDate today = LocalDate.now(clock);
        LocalDate effectiveTo = to != null ? to : today;
        LocalDate effectiveFrom = from != null
                ? from
                : effectiveTo.minusWeeks(DEFAULT_TIMELINE_WEEKS);

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new JobPilotException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        }

        Instant fromInclusive = effectiveFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = effectiveTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Application> apps = analyticsRepository.findForTimeline(userId, fromInclusive, toExclusive);

        Map<LocalDate, long[]> buckets = new TreeMap<>();
        for (LocalDate cursor = periodStart(effectiveFrom, effectiveBucket);
             !cursor.isAfter(effectiveTo);
             cursor = nextPeriod(cursor, effectiveBucket)) {
            buckets.put(cursor, new long[]{0L, 0L});
        }

        for (Application app : apps) {
            if (app.getCreatedAt() != null
                    && !app.getCreatedAt().isBefore(fromInclusive)
                    && app.getCreatedAt().isBefore(toExclusive)) {
                LocalDate day = LocalDate.ofInstant(app.getCreatedAt(), ZoneOffset.UTC);
                LocalDate key = periodStart(day, effectiveBucket);
                long[] counts = buckets.computeIfAbsent(key, k -> new long[]{0L, 0L});
                counts[0]++;
            }
            if (app.getAppliedAt() != null
                    && !app.getAppliedAt().isBefore(fromInclusive)
                    && app.getAppliedAt().isBefore(toExclusive)) {
                LocalDate day = LocalDate.ofInstant(app.getAppliedAt(), ZoneOffset.UTC);
                LocalDate key = periodStart(day, effectiveBucket);
                long[] counts = buckets.computeIfAbsent(key, k -> new long[]{0L, 0L});
                counts[1]++;
            }
        }

        List<TimelinePoint> points = buckets.entrySet().stream()
                .filter(e -> !e.getKey().isBefore(periodStart(effectiveFrom, effectiveBucket))
                        && !e.getKey().isAfter(effectiveTo))
                .map(e -> new TimelinePoint(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();

        return new AnalyticsTimelineResponse(effectiveBucket, effectiveFrom, effectiveTo, points);
    }

    @Transactional(readOnly = true)
    public SkillsGapResponse skillsGap(UUID userId) {
        List<UUID> applicationIds = analyticsRepository.findApplicationIdsByUserId(userId);
        if (applicationIds.isEmpty()) {
            return new SkillsGapResponse(List.of());
        }

        List<JobRequirement> requirements = jobRequirementRepository.findByApplicationIdIn(applicationIds);
        if (requirements.isEmpty()) {
            return new SkillsGapResponse(List.of());
        }

        Map<UUID, List<String>> requiredByApp = requirements.stream()
                .collect(Collectors.groupingBy(
                        JobRequirement::getApplicationId,
                        Collectors.mapping(JobRequirement::getSkillName, Collectors.toList())
                ));

        List<String> userSkills = skillRepository.findByUserIdOrderByNameAsc(userId).stream()
                .map(Skill::getName)
                .toList();

        Map<String, Long> missingCounts = new HashMap<>();
        for (List<String> required : requiredByApp.values()) {
            JobMatchResponse match = jobMatchEngine.match(required, userSkills);
            for (String missing : match.missingSkills()) {
                missingCounts.merge(missing, 1L, Long::sum);
            }
        }

        List<SkillGapItem> top = missingCounts.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(DEFAULT_SKILLS_GAP_LIMIT)
                .map(e -> new SkillGapItem(e.getKey(), e.getValue()))
                .toList();

        return new SkillsGapResponse(top);
    }

    private Map<ApplicationStatus, Long> statusCounts(UUID userId) {
        Map<ApplicationStatus, Long> map = new EnumMap<>(ApplicationStatus.class);
        for (Object[] row : analyticsRepository.countGroupedByStatus(userId)) {
            ApplicationStatus status = (ApplicationStatus) row[0];
            long count = (Long) row[1];
            map.put(status, count);
        }
        return map;
    }

    private Double computeAverageMatchScore(UUID userId) {
        List<UUID> applicationIds = analyticsRepository.findApplicationIdsByUserId(userId);
        if (applicationIds.isEmpty()) {
            return null;
        }

        List<JobRequirement> requirements = jobRequirementRepository.findByApplicationIdIn(applicationIds);
        if (requirements.isEmpty()) {
            return null;
        }

        Map<UUID, List<String>> requiredByApp = new LinkedHashMap<>();
        for (JobRequirement req : requirements) {
            requiredByApp
                    .computeIfAbsent(req.getApplicationId(), id -> new ArrayList<>())
                    .add(req.getSkillName());
        }

        List<String> userSkills = skillRepository.findByUserIdOrderByNameAsc(userId).stream()
                .map(Skill::getName)
                .toList();

        int sum = 0;
        int n = 0;
        for (List<String> required : requiredByApp.values()) {
            sum += jobMatchEngine.match(required, userSkills).score();
            n++;
        }
        if (n == 0) {
            return null;
        }
        return roundRate((double) sum / n);
    }

    private static LocalDate periodStart(LocalDate date, TimelineBucket bucket) {
        return switch (bucket) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    private static LocalDate nextPeriod(LocalDate periodStart, TimelineBucket bucket) {
        return switch (bucket) {
            case DAY -> periodStart.plusDays(1);
            case WEEK -> periodStart.plusWeeks(1);
            case MONTH -> periodStart.plusMonths(1);
        };
    }

    private static Double roundRate(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
