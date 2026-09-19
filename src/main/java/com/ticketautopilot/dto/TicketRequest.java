package com.ticketautopilot.dto;

import jakarta.validation.constraints.NotBlank;

public record TicketRequest(
        @NotBlank String subject,
        @NotBlank String body,
        String engine
) {
}
