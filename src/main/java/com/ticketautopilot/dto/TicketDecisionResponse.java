package com.ticketautopilot.dto;

import com.ticketautopilot.domain.Decision;
import com.ticketautopilot.domain.Ticket;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TicketDecisionResponse(
        UUID ticketId,
        String subject,
        String body,
        String category,
        double categoryConfidence,
        String urgency,
        double urgencyConfidence,
        boolean autoResolvable,
        double autoResolvableConfidence,
        String action,
        String engineUsed,
        int latencyMs,
        Instant decidedAt,
        Map<String, Double> categoryProbabilities,
        Map<String, Double> urgencyProbabilities
) {
    public static TicketDecisionResponse from(Ticket ticket, Decision decision) {
        return new TicketDecisionResponse(
                ticket.getId(), ticket.getSubject(), ticket.getBody(),
                decision.getCategory(), decision.getCategoryConfidence(),
                decision.getUrgency(), decision.getUrgencyConfidence(),
                decision.isAutoResolvable(), decision.getAutoResolvableConfidence(),
                decision.getAction(), decision.getEngineUsed(),
                decision.getLatencyMs(), decision.getDecidedAt(),
                decision.getCategoryProbabilities(), decision.getUrgencyProbabilities()
        );
    }
}
