package com.ticketautopilot.engine;

public record TicketDecision(
        String category,
        double categoryConfidence,
        String urgency,
        double urgencyConfidence,
        boolean autoResolvable,
        double autoResolvableConfidence,
        String action,
        String engineUsed,
        long latencyMs
) {
    public TicketDecision withEngineUsed(String newEngineUsed) {
        return withEngineUsedAndAction(newEngineUsed, action);
    }

    public TicketDecision withEngineUsedAndAction(String newEngineUsed, String newAction) {
        return new TicketDecision(
                category, categoryConfidence, urgency, urgencyConfidence,
                autoResolvable, autoResolvableConfidence,
                newAction, newEngineUsed, latencyMs
        );
    }
}
