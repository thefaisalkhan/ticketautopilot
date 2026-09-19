package com.ticketautopilot.service;

import com.ticketautopilot.domain.Decision;
import com.ticketautopilot.dto.AnalyticsSummaryResponse;
import com.ticketautopilot.repository.DecisionRepository;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aggregates every logged decision into summary stats for the analytics
 * dashboard. Pulls the full decisions table into memory and aggregates with
 * plain Java streams rather than SQL group-bys — this app's decision volume
 * is demo/testing scale, not production scale, so the simpler, more
 * testable approach wins; a high-volume deployment would push this down
 * into the database.
 */
@Service
public class AnalyticsService {

    private static final String JEV = "jev";
    private static final String RULE_BASED = "rule_based";

    private final DecisionRepository decisionRepository;

    public AnalyticsService(DecisionRepository decisionRepository) {
        this.decisionRepository = decisionRepository;
    }

    public AnalyticsSummaryResponse summarize() {
        List<Decision> all = decisionRepository.findAll();
        long total = all.size();

        if (total == 0) {
            return new AnalyticsSummaryResponse(0, 0, 0, Map.of(), Map.of(), Map.of(), Map.of(), null, 0, List.of());
        }

        long autoRouted = all.stream().filter(d -> "auto_route".equals(d.getAction())).count();
        double autoRoutedPercentage = (double) autoRouted / total;

        double avgConfidence = all.stream()
                .mapToDouble(d -> (d.getCategoryConfidence() + d.getUrgencyConfidence() + d.getAutoResolvableConfidence()) / 3.0)
                .average()
                .orElse(0);

        Map<String, Double> avgLatencyByEngine = all.stream()
                .collect(Collectors.groupingBy(Decision::getEngineUsed, Collectors.averagingInt(Decision::getLatencyMs)));

        Map<String, Long> categoryDistribution = all.stream()
                .collect(Collectors.groupingBy(Decision::getCategory, Collectors.counting()));

        Map<String, Long> urgencyDistribution = all.stream()
                .collect(Collectors.groupingBy(Decision::getUrgency, Collectors.counting()));

        Map<String, Long> engineUsageDistribution = all.stream()
                .collect(Collectors.groupingBy(Decision::getEngineUsed, Collectors.counting()));

        Agreement agreement = computeAgreement(all);

        // decidedAt is only assigned by @PrePersist, so a freshly constructed
        // (not-yet-persisted) Decision — which only happens in tests — has
        // none; filtered out rather than risking an NPE.
        Map<String, Long> byDay = all.stream()
                .filter(d -> d.getDecidedAt() != null)
                .collect(Collectors.groupingBy(
                        d -> d.getDecidedAt().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE),
                        Collectors.counting()
                ));
        List<AnalyticsSummaryResponse.DailyCount> decisionsPerDay = byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new AnalyticsSummaryResponse.DailyCount(e.getKey(), e.getValue()))
                .toList();

        return new AnalyticsSummaryResponse(
                total, autoRoutedPercentage, avgConfidence, avgLatencyByEngine,
                categoryDistribution, urgencyDistribution, engineUsageDistribution,
                agreement.rate(), agreement.pairCount(), decisionsPerDay
        );
    }

    /**
     * Agreement rate only makes sense between Jev and rule-based decisions
     * logged against the *same* ticket — which only happens via Compare-both
     * (the schema allows multiple decisions per ticket precisely for this).
     * Fallback/fast-path rows are excluded: they're already rule-based
     * results wearing a different label, not an independent second opinion.
     */
    private Agreement computeAgreement(List<Decision> all) {
        Map<UUID, List<Decision>> byTicket = all.stream()
                .filter(d -> JEV.equals(d.getEngineUsed()) || RULE_BASED.equals(d.getEngineUsed()))
                .collect(Collectors.groupingBy(Decision::getTicketId));

        long pairCount = 0;
        long agreeing = 0;
        for (List<Decision> decisions : byTicket.values()) {
            Decision jev = decisions.stream().filter(d -> JEV.equals(d.getEngineUsed())).findFirst().orElse(null);
            Decision ruleBased = decisions.stream().filter(d -> RULE_BASED.equals(d.getEngineUsed())).findFirst().orElse(null);
            if (jev == null || ruleBased == null) {
                continue;
            }
            pairCount++;
            boolean agree = jev.getCategory().equals(ruleBased.getCategory())
                    && jev.getUrgency().equals(ruleBased.getUrgency())
                    && jev.getAction().equals(ruleBased.getAction());
            if (agree) {
                agreeing++;
            }
        }

        Double rate = pairCount == 0 ? null : (double) agreeing / pairCount;
        return new Agreement(rate, pairCount);
    }

    private record Agreement(Double rate, long pairCount) {
    }
}
