package com.ticketautopilot.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketautopilot.domain.Ticket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls Jev (TypeSafe AI's decision model) via OpenRouter's alpha
 * "decisions" endpoint — confirmed by a manual test call to differ from
 * OpenRouter's standard chat-completions shape: it's a dedicated endpoint,
 * the model id needs a leading "~", the boolean-ish primitive is typed
 * "noul" (and its answer field is literally named "noul", not
 * "probability"), and urgency.score is a continuous float across the 0-3
 * rubric rather than a discrete int.
 */
@Component
public class JevDecisionEngine implements DecisionEngine {

    private static final Map<String, String> CATEGORY_CRITERIA = new LinkedHashMap<>();

    static {
        CATEGORY_CRITERIA.put("billing", "Charges, invoices, refunds");
        CATEGORY_CRITERIA.put("bug", "Software errors or broken functionality");
        CATEGORY_CRITERIA.put("feature_request", "Request for new functionality");
        CATEGORY_CRITERIA.put("how_to", "Asking how to use something");
        CATEGORY_CRITERIA.put("account", null);
    }

    private static final List<String> URGENCY_CRITERIA = List.of(
            "Low — can wait", "Normal", "High — needs prompt attention", "Critical — blocking/urgent"
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final TicketDecisionAggregator aggregator;
    private final String modelId;

    public JevDecisionEngine(TicketDecisionAggregator aggregator,
                              ObjectMapper objectMapper,
                              @Value("${ticketautopilot.jev.base-url}") String baseUrl,
                              @Value("${ticketautopilot.jev.api-key}") String apiKey,
                              @Value("${ticketautopilot.jev.model-id}") String modelId) {
        this.aggregator = aggregator;
        this.objectMapper = objectMapper;
        this.modelId = modelId;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public TicketDecision evaluate(Ticket ticket) {
        long start = System.nanoTime();

        String rawResponse;
        try {
            rawResponse = restClient.post()
                    .body(buildRequestBody(ticket))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new JevEngineException("Jev call failed: " + e.getMessage(), e);
        }

        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return parseResponse(rawResponse, latencyMs);
    }

    @Override
    public String engineName() {
        return "jev";
    }

    Map<String, Object> buildRequestBody(Ticket ticket) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("subject", ticket.getSubject());
        state.put("body", ticket.getBody());

        Map<String, Object> categoryQuestion = new LinkedHashMap<>();
        categoryQuestion.put("type", "choice");
        categoryQuestion.put("instructions", "Which category best fits this ticket?");
        categoryQuestion.put("criteria", CATEGORY_CRITERIA);

        Map<String, Object> urgencyQuestion = new LinkedHashMap<>();
        urgencyQuestion.put("type", "score");
        urgencyQuestion.put("instructions", "How urgent is this ticket?");
        urgencyQuestion.put("criteria", URGENCY_CRITERIA);

        Map<String, Object> autoResolvableQuestion = new LinkedHashMap<>();
        autoResolvableQuestion.put("type", "noul");
        autoResolvableQuestion.put("instructions",
                "Can this be resolved by pointing to an FAQ/help doc without human intervention?");

        Map<String, Object> questions = new LinkedHashMap<>();
        questions.put("category", categoryQuestion);
        questions.put("urgency", urgencyQuestion);
        questions.put("is_auto_resolvable", autoResolvableQuestion);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelId);
        body.put("state", state);
        body.put("questions", questions);
        return body;
    }

    TicketDecision parseResponse(String rawResponse, long latencyMs) {
        try {
            JsonNode answers = objectMapper.readTree(rawResponse).path("answers");
            if (answers.isMissingNode()) {
                throw new IllegalStateException("response has no \"answers\" field");
            }

            String category = answers.path("category").path("choice").asText();
            if (category.isBlank()) {
                throw new IllegalStateException("response has no answers.category.choice");
            }
            double categoryConfidence = answers.path("category").path("confidence").asDouble();

            double urgencyScoreRaw = answers.path("urgency").path("score").asDouble();
            double urgencyConfidence = answers.path("urgency").path("confidence").asDouble();
            int urgencyScore = clampUrgencyScore(Math.round(urgencyScoreRaw));

            double autoResolvableProbability = answers.path("is_auto_resolvable").path("noul").asDouble();
            boolean autoResolvable = aggregator.deriveAutoResolvable(autoResolvableProbability);
            double autoResolvableConfidence =
                    aggregator.deriveAutoResolvableConfidence(autoResolvableProbability, autoResolvable);

            String urgencyLabel = aggregator.mapUrgencyScoreToLabel(urgencyScore);
            String action = aggregator.decideAction(autoResolvable, categoryConfidence, urgencyConfidence, autoResolvableConfidence);

            Map<String, Double> categoryProbabilities = parseProbabilities(answers.path("category").path("probabilities"));
            Map<String, Double> urgencyProbabilities = parseUrgencyProbabilities(answers.path("urgency").path("probabilities"));

            return new TicketDecision(
                    category, categoryConfidence,
                    urgencyLabel, urgencyConfidence,
                    autoResolvable, autoResolvableConfidence,
                    action, engineName(), latencyMs,
                    categoryProbabilities, urgencyProbabilities
            );
        } catch (Exception e) {
            throw new JevEngineException("Failed to parse Jev response: " + rawResponse, e);
        }
    }

    private int clampUrgencyScore(long rounded) {
        return (int) Math.max(0, Math.min(3, rounded));
    }

    /**
     * Category probabilities come back keyed by category name already
     * (e.g. {"billing": 1, "bug": 0, ...}) — copied through as-is.
     */
    private Map<String, Double> parseProbabilities(JsonNode probabilitiesNode) {
        if (probabilitiesNode.isMissingNode() || !probabilitiesNode.isObject()) {
            return null;
        }
        Map<String, Double> probabilities = new LinkedHashMap<>();
        probabilitiesNode.fields().forEachRemaining(entry -> probabilities.put(entry.getKey(), entry.getValue().asDouble()));
        return probabilities;
    }

    /**
     * Urgency probabilities come back keyed by rubric position ("0".."3"),
     * e.g. {"0": 0.05, "1": 0.83, "2": 0.12, "3": 0}. Remapped to the same
     * low/normal/high/critical labels used everywhere else so this data is
     * self-explanatory without cross-referencing the rubric. A key that
     * doesn't parse as 0-3 is skipped rather than failing the whole
     * response, since this is supplementary display data, not something
     * the auto-route decision depends on.
     */
    private Map<String, Double> parseUrgencyProbabilities(JsonNode probabilitiesNode) {
        if (probabilitiesNode.isMissingNode() || !probabilitiesNode.isObject()) {
            return null;
        }
        Map<String, Double> probabilities = new LinkedHashMap<>();
        probabilitiesNode.fields().forEachRemaining(entry -> {
            try {
                int score = Integer.parseInt(entry.getKey());
                String label = aggregator.mapUrgencyScoreToLabel(score);
                probabilities.put(label, entry.getValue().asDouble());
            } catch (IllegalArgumentException ignored) {
                // Not a 0-3 rubric position; skip rather than fail the whole parse.
            }
        });
        return probabilities;
    }
}
