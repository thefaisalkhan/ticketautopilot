package com.ticketautopilot.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketautopilot.dto.CandidateRankingResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Scores one candidate resume against one job posting via Jev, reusing the
 * same Choice/Score/Noul request shape and probability-distribution parsing
 * proven out in JevDecisionEngine for ticket triage — same "code decides,
 * not the model" pattern, pointed at a different kind of event.
 */
@Component
public class JevCandidateRankingEngine {

    private static final Map<String, String> FIT_TIER_CRITERIA = new LinkedHashMap<>();

    static {
        FIT_TIER_CRITERIA.put("not_a_fit", "Missing multiple must-have requirements");
        FIT_TIER_CRITERIA.put("potential_fit", "Meets most requirements, some gaps");
        FIT_TIER_CRITERIA.put("strong_fit", "Meets or exceeds the job's requirements");
    }

    private static final String[] MATCH_SCORE_LABELS = {"Poor fit", "Fair fit", "Good fit", "Excellent fit"};

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String modelId;

    public JevCandidateRankingEngine(ObjectMapper objectMapper,
                                      @Value("${ticketautopilot.jev.base-url}") String baseUrl,
                                      @Value("${ticketautopilot.jev.api-key}") String apiKey,
                                      @Value("${ticketautopilot.jev.model-id}") String modelId) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.modelId = modelId;
    }

    public CandidateRankingResult evaluate(String jobTitle, String jobDescription,
                                            String candidateName, String resume) {
        long start = System.nanoTime();

        String rawResponse;
        try {
            rawResponse = restClient.post()
                    .body(buildRequestBody(jobTitle, jobDescription, candidateName, resume))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new JevEngineException("Jev call failed for candidate \"" + candidateName + "\": " + e.getMessage(), e);
        }

        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return parseResponse(rawResponse, candidateName, latencyMs);
    }

    Map<String, Object> buildRequestBody(String jobTitle, String jobDescription, String candidateName, String resume) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("job_title", jobTitle);
        state.put("job_description", jobDescription);
        state.put("candidate_name", candidateName);
        state.put("resume", resume);

        Map<String, Object> fitTierQuestion = new LinkedHashMap<>();
        fitTierQuestion.put("type", "choice");
        fitTierQuestion.put("instructions", "How well does this candidate's resume fit the job's requirements?");
        fitTierQuestion.put("criteria", FIT_TIER_CRITERIA);

        Map<String, Object> matchScoreQuestion = new LinkedHashMap<>();
        matchScoreQuestion.put("type", "score");
        matchScoreQuestion.put("instructions", "Rate the overall strength of this candidate's match to the job.");
        matchScoreQuestion.put("criteria", java.util.List.of(MATCH_SCORE_LABELS));

        Map<String, Object> recommendQuestion = new LinkedHashMap<>();
        recommendQuestion.put("type", "noul");
        recommendQuestion.put("instructions", "Should this candidate be recommended for an interview?");

        Map<String, Object> questions = new LinkedHashMap<>();
        questions.put("fit_tier", fitTierQuestion);
        questions.put("match_score", matchScoreQuestion);
        questions.put("recommend_interview", recommendQuestion);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelId);
        body.put("state", state);
        body.put("questions", questions);
        return body;
    }

    CandidateRankingResult parseResponse(String rawResponse, String candidateName, long latencyMs) {
        try {
            JsonNode answers = objectMapper.readTree(rawResponse).path("answers");
            if (answers.isMissingNode()) {
                throw new IllegalStateException("response has no \"answers\" field");
            }

            String fitTier = answers.path("fit_tier").path("choice").asText();
            if (fitTier.isBlank()) {
                throw new IllegalStateException("response has no answers.fit_tier.choice");
            }
            double fitTierConfidence = answers.path("fit_tier").path("confidence").asDouble();
            Map<String, Double> fitTierProbabilities = parseProbabilities(answers.path("fit_tier").path("probabilities"));

            JsonNode matchScoreProbabilitiesNode = answers.path("match_score").path("probabilities");
            double matchScoreConfidence = answers.path("match_score").path("confidence").asDouble();
            Map<String, Double> matchScoreProbabilities = parseMatchScoreProbabilities(matchScoreProbabilitiesNode);
            double matchScoreRaw = answers.path("match_score").path("score").asDouble();
            int matchScoreRounded = clampMatchScore(Math.round(matchScoreRaw));
            String matchScoreLabel = MATCH_SCORE_LABELS[matchScoreRounded];
            double expectedValue = expectedMatchScore(matchScoreProbabilitiesNode, matchScoreRaw);

            double recommendProbability = answers.path("recommend_interview").path("noul").asDouble();
            boolean recommend = recommendProbability > 0.5;
            double recommendConfidence = recommend ? recommendProbability : 1 - recommendProbability;

            return new CandidateRankingResult(
                    candidateName, fitTier, fitTierConfidence,
                    matchScoreLabel, expectedValue, matchScoreConfidence,
                    recommend, recommendConfidence,
                    fitTierProbabilities, matchScoreProbabilities,
                    latencyMs
            );
        } catch (Exception e) {
            throw new JevEngineException("Failed to parse Jev response for candidate \"" + candidateName + "\": " + rawResponse, e);
        }
    }

    private int clampMatchScore(long rounded) {
        return (int) Math.max(0, Math.min(MATCH_SCORE_LABELS.length - 1, rounded));
    }

    /**
     * Ranking multiple candidates off the rounded 0-3 score alone produces
     * lots of ties, so the finer-grained probability-weighted expected value
     * (sum of level * probability) is used to order candidates within the
     * same rounded tier — falling back to the raw score if probabilities
     * weren't returned.
     */
    private double expectedMatchScore(JsonNode probabilitiesNode, double rawScore) {
        if (probabilitiesNode.isMissingNode() || !probabilitiesNode.isObject() || probabilitiesNode.isEmpty()) {
            return rawScore;
        }
        double[] expected = {0};
        probabilitiesNode.fields().forEachRemaining(entry -> {
            try {
                int level = Integer.parseInt(entry.getKey());
                expected[0] += level * entry.getValue().asDouble();
            } catch (NumberFormatException ignored) {
                // Not a 0-3 rubric position; skip.
            }
        });
        return expected[0];
    }

    private Map<String, Double> parseProbabilities(JsonNode probabilitiesNode) {
        if (probabilitiesNode.isMissingNode() || !probabilitiesNode.isObject()) {
            return null;
        }
        Map<String, Double> probabilities = new LinkedHashMap<>();
        probabilitiesNode.fields().forEachRemaining(entry -> probabilities.put(entry.getKey(), entry.getValue().asDouble()));
        return probabilities;
    }

    /**
     * Match-score probabilities come back keyed by rubric position ("0".."3"),
     * remapped to the human-readable labels for display, same convention as
     * ticket urgency probabilities.
     */
    private Map<String, Double> parseMatchScoreProbabilities(JsonNode probabilitiesNode) {
        if (probabilitiesNode.isMissingNode() || !probabilitiesNode.isObject()) {
            return null;
        }
        Map<String, Double> probabilities = new LinkedHashMap<>();
        probabilitiesNode.fields().forEachRemaining(entry -> {
            try {
                int level = Integer.parseInt(entry.getKey());
                probabilities.put(MATCH_SCORE_LABELS[clampMatchScore(level)], entry.getValue().asDouble());
            } catch (NumberFormatException ignored) {
                // Not a 0-3 rubric position; skip.
            }
        });
        return probabilities;
    }
}
