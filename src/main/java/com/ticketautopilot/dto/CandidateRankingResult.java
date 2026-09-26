package com.ticketautopilot.dto;

import java.util.Map;

public record CandidateRankingResult(
        String candidateName,
        String fitTier,
        double fitTierConfidence,
        String matchScoreLabel,
        double matchScoreExpectedValue,
        double matchScoreConfidence,
        boolean recommendInterview,
        double recommendInterviewConfidence,
        Map<String, Double> fitTierProbabilities,
        Map<String, Double> matchScoreProbabilities,
        long latencyMs
) {
}
