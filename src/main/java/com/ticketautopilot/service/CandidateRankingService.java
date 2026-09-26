package com.ticketautopilot.service;

import com.ticketautopilot.dto.CandidateInput;
import com.ticketautopilot.dto.CandidateRankingResult;
import com.ticketautopilot.engine.JevCandidateRankingEngine;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class CandidateRankingService {

    private final JevCandidateRankingEngine engine;

    public CandidateRankingService(JevCandidateRankingEngine engine) {
        this.engine = engine;
    }

    public List<CandidateRankingResult> rank(String jobTitle, String jobDescription, List<CandidateInput> candidates) {
        return candidates.stream()
                .map(candidate -> engine.evaluate(jobTitle, jobDescription, candidate.name(), candidate.resume()))
                .sorted(Comparator
                        .comparingDouble(CandidateRankingResult::matchScoreExpectedValue)
                        .thenComparingDouble(CandidateRankingResult::fitTierConfidence)
                        .reversed())
                .toList();
    }
}
