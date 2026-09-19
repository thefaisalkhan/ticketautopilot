package com.ticketautopilot.service;

import com.ticketautopilot.domain.Decision;
import com.ticketautopilot.domain.Ticket;
import com.ticketautopilot.dto.TicketCompareResponse;
import com.ticketautopilot.dto.TicketDecisionResponse;
import com.ticketautopilot.engine.DecisionEngine;
import com.ticketautopilot.engine.JevEngineException;
import com.ticketautopilot.engine.TicketDecision;
import com.ticketautopilot.repository.DecisionRepository;
import com.ticketautopilot.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TriageServiceTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private DecisionRepository decisionRepository;
    @Mock
    private DecisionEngine jevEngine;
    @Mock
    private DecisionEngine ruleBasedEngine;
    @Mock
    private FaqFastPathMatcher faqFastPathMatcher;

    private TriageService triageService;

    @BeforeEach
    void setUp() {
        lenient().when(jevEngine.engineName()).thenReturn("jev");
        lenient().when(ruleBasedEngine.engineName()).thenReturn("rule_based");

        // Echo back whatever gets saved, as a real JPA repository would after persisting.
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(decisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        triageService = new TriageService(
                ticketRepository, decisionRepository,
                List.of(jevEngine, ruleBasedEngine), faqFastPathMatcher
        );
    }

    @Test
    void triageWithRuleBasedEngineUsesItDirectly() {
        TicketDecision decision = new TicketDecision(
                "billing", 0.9, "high", 0.9, false, 0.7,
                "needs_human_review", "rule_based", 5
        );
        when(ruleBasedEngine.evaluate(any())).thenReturn(decision);

        TicketDecisionResponse response = triageService.triage("Subject", "Body", "rule_based");

        assertThat(response.engineUsed()).isEqualTo("rule_based");
        assertThat(response.action()).isEqualTo("needs_human_review");
        verify(jevEngine, never()).evaluate(any());
    }

    @Test
    void triageWithJevEngineCallsJevWhenNotAFastPathMatch() {
        when(faqFastPathMatcher.matches(any())).thenReturn(false);
        TicketDecision decision = new TicketDecision(
                "bug", 1.0, "critical", 1.0, false, 0.9,
                "needs_human_review", "jev", 300
        );
        when(jevEngine.evaluate(any())).thenReturn(decision);

        TicketDecisionResponse response = triageService.triage("Subject", "Body", "jev");

        assertThat(response.engineUsed()).isEqualTo("jev");
        verify(ruleBasedEngine, never()).evaluate(any());
    }

    @Test
    void costRoutingFastPathSkipsJevEntirely() {
        when(faqFastPathMatcher.matches(any())).thenReturn(true);
        TicketDecision decision = new TicketDecision(
                "account", 0.95, "low", 0.89, true, 0.95,
                "auto_route", "rule_based", 0
        );
        when(ruleBasedEngine.evaluate(any())).thenReturn(decision);

        TicketDecisionResponse response = triageService.triage("How do I reset password", "reset password please", "jev");

        assertThat(response.engineUsed()).isEqualTo("rule_based_fast_path");
        verify(jevEngine, never()).evaluate(any());
    }

    @Test
    void jevFailureFallsBackAndForcesNeedsHumanReviewEvenIfRuleBasedWouldAutoRoute() {
        when(faqFastPathMatcher.matches(any())).thenReturn(false);
        when(jevEngine.evaluate(any())).thenThrow(new JevEngineException("boom", new RuntimeException()));

        // Rule-based engine's own opinion is a confident auto_route — the fallback
        // must override this to needs_human_review regardless.
        TicketDecision ruleBasedOpinion = new TicketDecision(
                "how_to", 0.95, "critical", 0.89, true, 0.95,
                "auto_route", "rule_based", 0
        );
        when(ruleBasedEngine.evaluate(any())).thenReturn(ruleBasedOpinion);

        TicketDecisionResponse response = triageService.triage("Subject", "Body", "jev");

        assertThat(response.engineUsed()).isEqualTo("fallback");
        assertThat(response.action()).isEqualTo("needs_human_review");
        // The underlying rule-based classification itself is preserved, only the
        // engine label and action are overridden.
        assertThat(response.category()).isEqualTo("how_to");
    }

    @Test
    void unknownEngineNameIsRejected() {
        assertThatThrownBy(() -> triageService.triage("Subject", "Body", "not_a_real_engine"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compareRunsBothEnginesAndPersistsTwoDecisionsForTheSameTicket() {
        when(faqFastPathMatcher.matches(any())).thenReturn(false);
        TicketDecision jevDecision = new TicketDecision(
                "bug", 1.0, "critical", 1.0, false, 0.92,
                "needs_human_review", "jev", 300
        );
        TicketDecision ruleBasedDecision = new TicketDecision(
                "account", 0.89, "critical", 0.95, false, 0.7,
                "needs_human_review", "rule_based", 0
        );
        when(jevEngine.evaluate(any())).thenReturn(jevDecision);
        when(ruleBasedEngine.evaluate(any())).thenReturn(ruleBasedDecision);

        TicketCompareResponse response = triageService.compare("Subject", "Body");

        assertThat(response.jev().category()).isEqualTo("bug");
        assertThat(response.ruleBased().category()).isEqualTo("account");
        verify(decisionRepository, times(2)).save(any(Decision.class));
    }
}
