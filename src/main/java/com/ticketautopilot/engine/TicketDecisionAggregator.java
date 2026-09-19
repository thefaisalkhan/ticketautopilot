package com.ticketautopilot.engine;

import org.springframework.stereotype.Component;

/**
 * Turns atomic per-question answers (a category choice, an urgency score,
 * an auto-resolvable probability — whichever engine produced them) into a
 * final action. Kept as shared, deterministic code rather than delegated to
 * Jev, so the auto-route bar is consistent and auditable across engines.
 */
@Component
public class TicketDecisionAggregator {

    public static final double CONFIDENCE_THRESHOLD = 0.75;

    public String mapUrgencyScoreToLabel(int score) {
        return switch (score) {
            case 0 -> "low";
            case 1 -> "normal";
            case 2 -> "high";
            case 3 -> "critical";
            default -> throw new IllegalArgumentException("Urgency score must be 0-3, got: " + score);
        };
    }

    public boolean deriveAutoResolvable(double probability) {
        return probability > 0.5;
    }

    public double deriveAutoResolvableConfidence(double probability, boolean autoResolvable) {
        return autoResolvable ? probability : 1 - probability;
    }

    public String decideAction(boolean autoResolvable, double categoryConfidence, double urgencyConfidence,
                                double autoResolvableConfidence) {
        // autoResolvable must actually be true here: a confident "this needs a
        // human" answer has high autoResolvableConfidence too, and must never
        // auto-route just because the model is sure about saying no.
        boolean confidentEnough = autoResolvable
                && categoryConfidence > CONFIDENCE_THRESHOLD
                && urgencyConfidence > CONFIDENCE_THRESHOLD
                && autoResolvableConfidence > CONFIDENCE_THRESHOLD;
        return confidentEnough ? "auto_route" : "needs_human_review";
    }
}
