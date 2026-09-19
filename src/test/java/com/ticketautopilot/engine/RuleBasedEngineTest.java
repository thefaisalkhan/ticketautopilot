package com.ticketautopilot.engine;

import com.ticketautopilot.domain.Ticket;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedEngineTest {

    private final RuleBasedEngine engine = new RuleBasedEngine(new TicketDecisionAggregator());

    @Test
    void reportsItsOwnName() {
        assertThat(engine.engineName()).isEqualTo("rule_based");
    }

    @Test
    void categorizesClearBillingKeywordsWithHighConfidence() {
        Ticket ticket = new Ticket(
                "Overcharged on my last invoice",
                "I was charged twice for my subscription, please refund the duplicate charge."
        );

        TicketDecision decision = engine.evaluate(ticket);

        assertThat(decision.category()).isEqualTo("billing");
        assertThat(decision.categoryConfidence()).isGreaterThan(0.75);
        assertThat(decision.engineUsed()).isEqualTo("rule_based");
    }

    @Test
    void autoRoutesAClearFaqTicketWithExplicitLowUrgency() {
        Ticket ticket = new Ticket(
                "How do I export my data to a spreadsheet?",
                "How to export all my data as a CSV file? Documentation would help, no rush."
        );

        TicketDecision decision = engine.evaluate(ticket);

        assertThat(decision.category()).isEqualTo("how_to");
        assertThat(decision.autoResolvable()).isTrue();
        assertThat(decision.action()).isEqualTo("auto_route");
    }

    @Test
    void needsHumanReviewWhenUrgencyHasNoExplicitSignal() {
        // Same FAQ content as above but with no urgency keyword at all: urgency
        // confidence defaults below the auto-route threshold, so even a clean
        // FAQ match should still go to a human.
        Ticket ticket = new Ticket(
                "How do I export my data to a spreadsheet?",
                "How to export all my data as a CSV file? Documentation would help."
        );

        TicketDecision decision = engine.evaluate(ticket);

        assertThat(decision.action()).isEqualTo("needs_human_review");
    }

    @Test
    void fallsBackToLowConfidenceCategoryWhenNoKeywordsMatch() {
        Ticket ticket = new Ticket("Random question", "Not really sure who to ask about this.");

        TicketDecision decision = engine.evaluate(ticket);

        assertThat(decision.category()).isEqualTo("how_to");
        assertThat(decision.categoryConfidence()).isEqualTo(0.4);
        assertThat(decision.action()).isEqualTo("needs_human_review");
    }

    @Test
    void neverAutoRoutesWhenNotAutoResolvableRegardlessOfOtherConfidences() {
        Ticket ticket = new Ticket(
                "App crashes immediately on login - production down",
                "Since this morning the app crashes as soon as I try to log in. This is blocking my "
                        + "entire team and needs urgent attention, it is critical."
        );

        TicketDecision decision = engine.evaluate(ticket);

        assertThat(decision.autoResolvable()).isFalse();
        assertThat(decision.action()).isEqualTo("needs_human_review");
    }

    @Test
    void neverFabricatesAProbabilityDistributionItDoesNotHave() {
        // Unlike Jev, the keyword baseline has no real probability distribution
        // over categories or urgency levels — it must leave these null rather
        // than inventing one that would look like real model output.
        Ticket ticket = new Ticket("Overcharged on my invoice", "Please refund the duplicate charge.");

        TicketDecision decision = engine.evaluate(ticket);

        assertThat(decision.categoryProbabilities()).isNull();
        assertThat(decision.urgencyProbabilities()).isNull();
    }
}
