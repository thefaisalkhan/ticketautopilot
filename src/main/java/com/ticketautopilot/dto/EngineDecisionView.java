package com.ticketautopilot.dto;

import com.ticketautopilot.domain.Decision;

public record EngineDecisionView(
        String category,
        double categoryConfidence,
        String urgency,
        double urgencyConfidence,
        boolean autoResolvable,
        double autoResolvableConfidence,
        String action,
        String engineUsed,
        int latencyMs
) {
    public static EngineDecisionView from(Decision decision) {
        return new EngineDecisionView(
                decision.getCategory(), decision.getCategoryConfidence(),
                decision.getUrgency(), decision.getUrgencyConfidence(),
                decision.isAutoResolvable(), decision.getAutoResolvableConfidence(),
                decision.getAction(), decision.getEngineUsed(), decision.getLatencyMs()
        );
    }
}
