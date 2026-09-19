package com.ticketautopilot.dto;

import java.util.UUID;

public record TicketCompareResponse(
        UUID ticketId,
        String subject,
        String body,
        EngineDecisionView jev,
        EngineDecisionView ruleBased
) {
}
