package com.ticketautopilot.dto;

import java.util.List;
import java.util.Map;

public record AnalyticsSummaryResponse(
        long totalDecisions,
        double autoRoutedPercentage,
        double avgConfidence,
        Map<String, Double> avgLatencyByEngine,
        Map<String, Long> categoryDistribution,
        Map<String, Long> urgencyDistribution,
        Map<String, Long> engineUsageDistribution,
        // null when no ticket has been triaged by both engines yet (needs at
        // least one Compare-both run) rather than a misleading 0%.
        Double agreementRate,
        long comparablePairCount,
        List<DailyCount> decisionsPerDay
) {
    public record DailyCount(String date, long count) {
    }
}
