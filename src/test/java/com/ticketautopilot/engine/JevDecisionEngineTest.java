package com.ticketautopilot.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketautopilot.domain.Ticket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JevDecisionEngineTest {

    private final JevDecisionEngine engine = new JevDecisionEngine(
            new TicketDecisionAggregator(),
            new ObjectMapper(),
            "https://openrouter.ai/api/alpha/decisions",
            "test-api-key",
            "~typesafe/jev-latest"
    );

    @Test
    void reportsItsOwnName() {
        assertThat(engine.engineName()).isEqualTo("jev");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildsARequestUsingJevsTypedQuestionShapeNotChatCompletions() {
        Ticket ticket = new Ticket("Overcharged on my invoice", "Please refund the duplicate charge.");

        Map<String, Object> body = engine.buildRequestBody(ticket);

        assertThat(body.get("model")).isEqualTo("~typesafe/jev-latest");

        Map<String, Object> state = (Map<String, Object>) body.get("state");
        assertThat(state.get("subject")).isEqualTo("Overcharged on my invoice");
        assertThat(state.get("body")).isEqualTo("Please refund the duplicate charge.");

        Map<String, Object> questions = (Map<String, Object>) body.get("questions");

        Map<String, Object> category = (Map<String, Object>) questions.get("category");
        assertThat(category.get("type")).isEqualTo("choice");
        Map<String, Object> criteria = (Map<String, Object>) category.get("criteria");
        assertThat(criteria.keySet()).containsExactlyInAnyOrder(
                "billing", "bug", "feature_request", "how_to", "account");
        assertThat(criteria.get("account")).isNull();

        Map<String, Object> urgency = (Map<String, Object>) questions.get("urgency");
        assertThat(urgency.get("type")).isEqualTo("score");
        assertThat((List<String>) urgency.get("criteria")).hasSize(4);

        Map<String, Object> autoResolvable = (Map<String, Object>) questions.get("is_auto_resolvable");
        // Jev's boolean-ish primitive is typed "noul", not "boolean" — confirmed by a
        // manual test call; the request must use the type Jev's schema actually accepts.
        assertThat(autoResolvable.get("type")).isEqualTo("noul");
    }

    @Test
    void parsesARealJevResponseIncludingTheNoulFieldAndFractionalScore() {
        // Captured verbatim from a real call to POST /api/alpha/decisions.
        String rawResponse = """
                {
                  "model": "typesafe/jev-1.13-20260917",
                  "answers": {
                    "category": {
                      "type": "choice",
                      "choice": "billing",
                      "probabilities": {"account":0,"billing":1,"how_to":0,"bug":0,"feature_request":0},
                      "confidence": 1
                    },
                    "urgency": {
                      "type": "score",
                      "score": 2.23,
                      "legend": {"0":"Low","1":"Normal","2":"High","3":"Critical"},
                      "probabilities": {"0":0,"1":0,"2":0.76,"3":0.24},
                      "confidence": 0.76
                    },
                    "is_auto_resolvable": {
                      "type": "noul",
                      "noul": 0.46
                    }
                  },
                  "usage": {"input_tokens":478,"output_tokens":89,"cost":0.000020076},
                  "id": "gen-dec-test",
                  "provider": "TypeSafe"
                }
                """;

        TicketDecision decision = engine.parseResponse(rawResponse, 250L);

        assertThat(decision.category()).isEqualTo("billing");
        assertThat(decision.categoryConfidence()).isEqualTo(1.0);
        // score 2.23 rounds to 2 -> "high", not truncated or read as a raw int.
        assertThat(decision.urgency()).isEqualTo("high");
        assertThat(decision.urgencyConfidence()).isEqualTo(0.76);
        // noul=0.46 <= 0.5 -> not auto-resolvable; confidence is the inverted probability (1 - 0.46).
        assertThat(decision.autoResolvable()).isFalse();
        assertThat(decision.autoResolvableConfidence()).isCloseTo(0.54, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(decision.engineUsed()).isEqualTo("jev");
        assertThat(decision.latencyMs()).isEqualTo(250L);

        // Full Choice/Score distributions, not just the winning value.
        assertThat(decision.categoryProbabilities()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "account", 0.0, "billing", 1.0, "how_to", 0.0, "bug", 0.0, "feature_request", 0.0
        ));
        // Urgency probabilities are keyed by rubric position ("0".."3") in the raw
        // response — remapped to the same low/normal/high/critical labels used
        // everywhere else, not left as opaque digit strings.
        assertThat(decision.urgencyProbabilities()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "low", 0.0, "normal", 0.0, "high", 0.76, "critical", 0.24
        ));
    }

    @Test
    void clampsUrgencyScoreIntoTheZeroToThreeRange() {
        TicketDecision aboveRange = engine.parseResponse(responseWithUrgencyScore(3.9), 0L);
        assertThat(aboveRange.urgency()).isEqualTo("critical");

        TicketDecision belowRange = engine.parseResponse(responseWithUrgencyScore(-0.6), 0L);
        assertThat(belowRange.urgency()).isEqualTo("low");
    }

    @Test
    void leavesProbabilitiesNullWhenTheResponseDoesNotIncludeThem() {
        // Older/minimal response shapes without "probabilities" shouldn't
        // fail the whole parse — this data is supplementary, not required
        // for the core auto-route decision.
        TicketDecision decision = engine.parseResponse(responseWithUrgencyScore(1.0), 0L);

        assertThat(decision.categoryProbabilities()).isNull();
        assertThat(decision.urgencyProbabilities()).isNull();
    }

    @Test
    void wrapsMalformedResponsesInAJevEngineException() {
        assertThatThrownBy(() -> engine.parseResponse("{not valid json", 0L))
                .isInstanceOf(JevEngineException.class);

        assertThatThrownBy(() -> engine.parseResponse("{}", 0L))
                .isInstanceOf(JevEngineException.class);
    }

    private String responseWithUrgencyScore(double score) {
        return """
                {
                  "answers": {
                    "category": {"choice": "bug", "confidence": 1},
                    "urgency": {"score": %s, "confidence": 1},
                    "is_auto_resolvable": {"noul": 0.1}
                  }
                }
                """.formatted(score);
    }
}
