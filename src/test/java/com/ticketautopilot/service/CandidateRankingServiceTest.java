package com.ticketautopilot.service;

import com.ticketautopilot.dto.CandidateInput;
import com.ticketautopilot.dto.CandidateRankingResult;
import com.ticketautopilot.engine.JevCandidateRankingEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateRankingServiceTest {

    @Mock
    private JevCandidateRankingEngine engine;

    @Test
    void ranksCandidatesByExpectedMatchScoreDescending() {
        CandidateRankingService service = new CandidateRankingService(engine);

        when(engine.evaluate(eq("Backend Engineer"), any(), eq("Weak Candidate"), any()))
                .thenReturn(resultFor("Weak Candidate", "not_a_fit", 0.9, 0.8));
        when(engine.evaluate(eq("Backend Engineer"), any(), eq("Strong Candidate"), any()))
                .thenReturn(resultFor("Strong Candidate", "strong_fit", 0.95, 2.7));
        when(engine.evaluate(eq("Backend Engineer"), any(), eq("Middling Candidate"), any()))
                .thenReturn(resultFor("Middling Candidate", "potential_fit", 0.7, 1.5));

        List<CandidateRankingResult> ranked = service.rank(
                "Backend Engineer", "Java, Spring Boot required.",
                List.of(
                        new CandidateInput("Weak Candidate", "..."),
                        new CandidateInput("Strong Candidate", "..."),
                        new CandidateInput("Middling Candidate", "...")
                ));

        assertThat(ranked).extracting(CandidateRankingResult::candidateName)
                .containsExactly("Strong Candidate", "Middling Candidate", "Weak Candidate");
    }

    @Test
    void tieBreaksEqualExpectedScoresByFitTierConfidence() {
        CandidateRankingService service = new CandidateRankingService(engine);

        when(engine.evaluate(eq("Backend Engineer"), any(), eq("Less Confident"), any()))
                .thenReturn(resultFor("Less Confident", "strong_fit", 0.6, 2.0));
        when(engine.evaluate(eq("Backend Engineer"), any(), eq("More Confident"), any()))
                .thenReturn(resultFor("More Confident", "strong_fit", 0.95, 2.0));

        List<CandidateRankingResult> ranked = service.rank(
                "Backend Engineer", "Java, Spring Boot required.",
                List.of(
                        new CandidateInput("Less Confident", "..."),
                        new CandidateInput("More Confident", "...")
                ));

        assertThat(ranked).extracting(CandidateRankingResult::candidateName)
                .containsExactly("More Confident", "Less Confident");
    }

    private CandidateRankingResult resultFor(String name, String fitTier, double fitTierConfidence, double expectedScore) {
        return new CandidateRankingResult(
                name, fitTier, fitTierConfidence,
                "Good fit", expectedScore, 0.8,
                true, 0.8,
                Map.of(), Map.of(),
                100L
        );
    }
}
