package com.ticketautopilot.dto;

import jakarta.validation.constraints.NotBlank;

public record CandidateInput(
        @NotBlank String name,
        @NotBlank String resume
) {
}
