package com.ticketautopilot.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketDecisionAggregatorTest {

    private final TicketDecisionAggregator aggregator = new TicketDecisionAggregator();

    @ParameterizedTest
    @CsvSource({
            "0, low",
            "1, normal",
            "2, high",
            "3, critical"
    })
    void mapsUrgencyScoreToLabel(int score, String expectedLabel) {
        assertThat(aggregator.mapUrgencyScoreToLabel(score)).isEqualTo(expectedLabel);
    }

    @Test
    void rejectsUrgencyScoreOutsideZeroToThree() {
        assertThatThrownBy(() -> aggregator.mapUrgencyScoreToLabel(4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> aggregator.mapUrgencyScoreToLabel(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void derivesAutoResolvableFromProbabilityAboveHalf() {
        assertThat(aggregator.deriveAutoResolvable(0.51)).isTrue();
        assertThat(aggregator.deriveAutoResolvable(0.5)).isFalse();
        assertThat(aggregator.deriveAutoResolvable(0.49)).isFalse();
    }

    @Test
    void confidenceIsTheProbabilityWhenAutoResolvableIsTrue() {
        assertThat(aggregator.deriveAutoResolvableConfidence(0.9, true)).isEqualTo(0.9);
    }

    @Test
    void confidenceIsInvertedProbabilityWhenAutoResolvableIsFalse() {
        assertThat(aggregator.deriveAutoResolvableConfidence(0.08, false)).isCloseTo(0.92, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void autoRoutesWhenAutoResolvableAndAllThreeConfidencesExceedThreshold() {
        String action = aggregator.decideAction(true, 0.9, 0.9, 0.9);
        assertThat(action).isEqualTo("auto_route");
    }

    @Test
    void neverAutoRoutesWhenAutoResolvableIsFalseEvenWithHighConfidence() {
        // Regression test: a ticket Jev confidently says needs a human (autoResolvable=false)
        // must never auto-route just because the model was highly confident in that "no" —
        // this exact case used to slip through before decideAction started checking
        // autoResolvable itself, not just the confidence magnitude.
        String action = aggregator.decideAction(false, 1.0, 1.0, 0.92);
        assertThat(action).isEqualTo("needs_human_review");
    }

    @Test
    void needsHumanReviewWhenAnyConfidenceIsAtOrBelowThreshold() {
        assertThat(aggregator.decideAction(true, 0.75, 0.9, 0.9)).isEqualTo("needs_human_review");
        assertThat(aggregator.decideAction(true, 0.9, 0.75, 0.9)).isEqualTo("needs_human_review");
        assertThat(aggregator.decideAction(true, 0.9, 0.9, 0.75)).isEqualTo("needs_human_review");
    }
}
