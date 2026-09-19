package com.ticketautopilot.service;

import com.ticketautopilot.domain.Decision;
import com.ticketautopilot.dto.AnalyticsSummaryResponse;
import com.ticketautopilot.repository.DecisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private DecisionRepository decisionRepository;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(decisionRepository);
    }

    @Test
    void returnsZeroedSummaryWhenNoDecisionsExist() {
        when(decisionRepository.findAll()).thenReturn(List.of());

        AnalyticsSummaryResponse summary = analyticsService.summarize();

        assertThat(summary.totalDecisions()).isZero();
        assertThat(summary.agreementRate()).isNull();
        assertThat(summary.categoryDistribution()).isEmpty();
    }

    @Test
    void aggregatesDistributionsAndAutoRoutePercentage() {
        UUID t1 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();
        UUID t3 = UUID.randomUUID();

        when(decisionRepository.findAll()).thenReturn(List.of(
                decision(t1, "billing", "high", "auto_route", "jev", 300),
                decision(t2, "bug", "critical", "needs_human_review", "jev", 250),
                decision(t3, "billing", "low", "needs_human_review", "rule_based", 0)
        ));

        AnalyticsSummaryResponse summary = analyticsService.summarize();

        assertThat(summary.totalDecisions()).isEqualTo(3);
        assertThat(summary.autoRoutedPercentage()).isCloseTo(1.0 / 3, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(summary.categoryDistribution()).containsEntry("billing", 2L).containsEntry("bug", 1L);
        assertThat(summary.engineUsageDistribution()).containsEntry("jev", 2L).containsEntry("rule_based", 1L);
        assertThat(summary.avgLatencyByEngine().get("jev")).isEqualTo(275.0);
    }

    @Test
    void computesAgreementRateOnlyFromComparablePairsOnTheSameTicket() {
        UUID agreeing = UUID.randomUUID();
        UUID disagreeing = UUID.randomUUID();
        UUID jevOnly = UUID.randomUUID();

        when(decisionRepository.findAll()).thenReturn(List.of(
                // Same ticket, both engines, fully agree.
                decision(agreeing, "billing", "high", "needs_human_review", "jev", 300),
                decision(agreeing, "billing", "high", "needs_human_review", "rule_based", 0),
                // Same ticket, both engines, disagree on category.
                decision(disagreeing, "bug", "critical", "needs_human_review", "jev", 300),
                decision(disagreeing, "account", "critical", "needs_human_review", "rule_based", 0),
                // Only jev ran on this ticket — not a comparable pair.
                decision(jevOnly, "how_to", "low", "auto_route", "jev", 200)
        ));

        AnalyticsSummaryResponse summary = analyticsService.summarize();

        assertThat(summary.comparablePairCount()).isEqualTo(2);
        assertThat(summary.agreementRate()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void fallbackAndFastPathDecisionsAreExcludedFromAgreementPairing() {
        UUID ticket = UUID.randomUUID();

        // A jev decision and a fallback decision on the same ticket is not a
        // real second opinion — fallback IS the rule-based engine, relabeled.
        when(decisionRepository.findAll()).thenReturn(List.of(
                decision(ticket, "billing", "high", "needs_human_review", "jev", 300),
                decision(ticket, "billing", "high", "needs_human_review", "fallback", 0)
        ));

        AnalyticsSummaryResponse summary = analyticsService.summarize();

        assertThat(summary.comparablePairCount()).isZero();
        assertThat(summary.agreementRate()).isNull();
    }

    private Decision decision(UUID ticketId, String category, String urgency, String action, String engineUsed, int latencyMs) {
        return new Decision(
                ticketId, category, 0.9, urgency, 0.9, action.equals("auto_route"), 0.9,
                action, engineUsed, latencyMs, null, null
        );
    }
}
