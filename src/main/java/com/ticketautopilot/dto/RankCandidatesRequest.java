package com.ticketautopilot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RankCandidatesRequest(
        @NotBlank String jobTitle,
        @NotBlank String jobDescription,
        @NotEmpty @Valid List<CandidateInput> candidates
) {
}
