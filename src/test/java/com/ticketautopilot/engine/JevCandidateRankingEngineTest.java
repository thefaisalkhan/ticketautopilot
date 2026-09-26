package com.ticketautopilot.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketautopilot.dto.CandidateRankingResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JevCandidateRankingEngineTest {

    private final JevCandidateRankingEngine engine = new JevCandidateRankingEngine(
            new ObjectMapper(),
            "https://openrouter.ai/api/alpha/decisions",
            "test-api-key",
            "~typesafe/jev-latest"
    );

    @Test
    @SuppressWarnings("unchecked")
    void buildsARequestUsingJevsTypedQuestionShape() {
        Map<String, Object> body = engine.buildRequestBody(
                "Senior Backend Engineer", "Java, Spring Boot, Kafka required.", "Ada Lovelace", "5 years Java.");

        assertThat(body.get("model")).isEqualTo("~typesafe/jev-latest");

        Map<String, Object> state = (Map<String, Object>) body.get("state");
        assertThat(state.get("job_title")).isEqualTo("Senior Backend Engineer");
        assertThat(state.get("candidate_name")).isEqualTo("Ada Lovelace");
        assertThat(state.get("resume")).isEqualTo("5 years Java.");

        Map<String, Object> questions = (Map<String, Object>) body.get("questions");

        Map<String, Object> fitTier = (Map<String, Object>) questions.get("fit_tier");
        assertThat(fitTier.get("type")).isEqualTo("choice");
        Map<String, Object> criteria = (Map<String, Object>) fitTier.get("criteria");
        assertThat(criteria.keySet()).containsExactlyInAnyOrder("not_a_fit", "potential_fit", "strong_fit");

        Map<String, Object> matchScore = (Map<String, Object>) questions.get("match_score");
        assertThat(matchScore.get("type")).isEqualTo("score");

        Map<String, Object> recommend = (Map<String, Object>) questions.get("recommend_interview");
        assertThat(recommend.get("type")).isEqualTo("noul");
    }

    @Test
    void parsesARealShapedJevResponseIntoARankingResult() {
        String rawResponse = """
                {
                  "answers": {
                    "fit_tier": {
                      "choice": "strong_fit",
                      "probabilities": {"not_a_fit": 0, "potential_fit": 0.1, "strong_fit": 0.9},
                      "confidence": 0.9
                    },
                    "match_score": {
                      "score": 2.6,
                      "probabilities": {"0": 0, "1": 0.05, "2": 0.35, "3": 0.6},
                      "confidence": 0.6
                    },
                    "recommend_interview": {
                      "noul": 0.88
                    }
                  }
                }
                """;

        CandidateRankingResult result = engine.parseResponse(rawResponse, "Ada Lovelace", 180L);

        assertThat(result.candidateName()).isEqualTo("Ada Lovelace");
        assertThat(result.fitTier()).isEqualTo("strong_fit");
        assertThat(result.fitTierConfidence()).isEqualTo(0.9);
        assertThat(result.matchScoreLabel()).isEqualTo("Excellent fit");
        // Expected value = 0*0 + 1*0.05 + 2*0.35 + 3*0.6 = 2.55, not the coarse rounded score.
        assertThat(result.matchScoreExpectedValue()).isCloseTo(2.55, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(result.recommendInterview()).isTrue();
        assertThat(result.recommendInterviewConfidence()).isEqualTo(0.88);
        assertThat(result.latencyMs()).isEqualTo(180L);

        assertThat(result.fitTierProbabilities()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "not_a_fit", 0.0, "potential_fit", 0.1, "strong_fit", 0.9
        ));
        assertThat(result.matchScoreProbabilities()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "Poor fit", 0.0, "Fair fit", 0.05, "Good fit", 0.35, "Excellent fit", 0.6
        ));
    }

    @Test
    void aConfidentNoStillProducesAnInvertedConfidenceNotTheRawProbability() {
        CandidateRankingResult result = engine.parseResponse(responseWithRecommendProbability(0.08), "Bob", 0L);

        assertThat(result.recommendInterview()).isFalse();
        assertThat(result.recommendInterviewConfidence()).isCloseTo(0.92, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void wrapsMalformedResponsesInAJevEngineException() {
        assertThatThrownBy(() -> engine.parseResponse("{not valid json", "Bob", 0L))
                .isInstanceOf(JevEngineException.class);

        assertThatThrownBy(() -> engine.parseResponse("{}", "Bob", 0L))
                .isInstanceOf(JevEngineException.class);
    }

    private String responseWithRecommendProbability(double noul) {
        return """
                {
                  "answers": {
                    "fit_tier": {"choice": "not_a_fit", "confidence": 0.9},
                    "match_score": {"score": 0.5, "confidence": 0.8},
                    "recommend_interview": {"noul": %s}
                  }
                }
                """.formatted(noul);
    }
}
