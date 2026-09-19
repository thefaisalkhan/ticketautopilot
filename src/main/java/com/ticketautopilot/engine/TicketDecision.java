package com.ticketautopilot.engine;

import java.util.Map;

public record TicketDecision(
        String category,
        double categoryConfidence,
        String urgency,
        double urgencyConfidence,
        boolean autoResolvable,
        double autoResolvableConfidence,
        String action,
        String engineUsed,
        long latencyMs,
        // Full Choice/Score probability distributions from Jev's "probabilities"
        // field (category name -> probability, urgency label -> probability).
        // Only Jev produces real distributions; RuleBasedEngine has no
        // equivalent and leaves these null rather than fabricating one.
        Map<String, Double> categoryProbabilities,
        Map<String, Double> urgencyProbabilities
) {
    public TicketDecision(String category, double categoryConfidence, String urgency, double urgencyConfidence,
                           boolean autoResolvable, double autoResolvableConfidence, String action,
                           String engineUsed, long latencyMs) {
        this(category, categoryConfidence, urgency, urgencyConfidence, autoResolvable, autoResolvableConfidence,
                action, engineUsed, latencyMs, null, null);
    }

    public TicketDecision withEngineUsed(String newEngineUsed) {
        return withEngineUsedAndAction(newEngineUsed, action);
    }

    public TicketDecision withEngineUsedAndAction(String newEngineUsed, String newAction) {
        return new TicketDecision(
                category, categoryConfidence, urgency, urgencyConfidence,
                autoResolvable, autoResolvableConfidence,
                newAction, newEngineUsed, latencyMs,
                categoryProbabilities, urgencyProbabilities
        );
    }
}
