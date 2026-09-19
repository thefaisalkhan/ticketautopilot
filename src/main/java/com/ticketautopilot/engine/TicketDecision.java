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
}
