package com.ticketautopilot.engine;

import com.ticketautopilot.domain.Ticket;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keyword/regex baseline engine. Produces the same TicketDecision shape as
 * JevDecisionEngine so both are interchangeable behind DecisionEngine, and
 * doubles as the automatic fallback when Jev is unavailable.
 */
@Component
public class RuleBasedEngine implements DecisionEngine {

    private static final double BASE_CONFIDENCE = 0.65;
    private static final double CONFIDENCE_PER_MATCH = 0.12;
    private static final double MAX_CONFIDENCE = 0.95;

    private static final Map<String, String[]> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        CATEGORY_KEYWORDS.put("billing", new String[]{
                "charge", "charged", "invoice", "refund", "payment", "bill", "billed",
                "overcharged", "credit card", "subscription cost", "billing cycle", "autopay", "price"
        });
        CATEGORY_KEYWORDS.put("bug", new String[]{
                "error", "bug", "broken", "crash", "not working", "doesn't work",
                "exception", "fails", "failed", "glitch", "freezes", "500 error",
                "not syncing", "won't load", "keeps crashing"
        });
        CATEGORY_KEYWORDS.put("feature_request", new String[]{
                "feature", "would be great", "add support", "please add",
                "suggestion", "enhancement", "it would be nice", "can you add",
                "feature request", "would love to see"
        });
        CATEGORY_KEYWORDS.put("how_to", new String[]{
                "how do i", "how to", "instructions", "guide", "documentation",
                "tutorial", "where is", "where can i", "not sure how"
        });
        CATEGORY_KEYWORDS.put("account", new String[]{
                "password", "login", "log in", "locked out", "sign in",
                "username", "2fa", "two-factor", "verify my email",
                "forgot my password", "reset my password", "can't access my account"
        });
    }

    private static final String[] CRITICAL_URGENCY_KEYWORDS = {
            "urgent", "critical", "asap", "blocking", "production down", "emergency",
            "immediately", "completely down", "losing money"
    };
    private static final String[] HIGH_URGENCY_KEYWORDS = {
            "important", "soon", "high priority", "affecting", "many users", "several customers"
    };
    private static final String[] LOW_URGENCY_KEYWORDS = {
            "whenever", "no rush", "low priority", "just curious", "minor", "not urgent"
    };

    private static final String[] FAQ_KEYWORDS = {
            "how do i", "how to", "reset my password", "forgot my password",
            "cancel my subscription", "cancel subscription", "where is",
            "documentation", "instructions", "guide"
    };

    private final TicketDecisionAggregator aggregator;

    public RuleBasedEngine(TicketDecisionAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @Override
    public TicketDecision evaluate(Ticket ticket) {
        long start = System.nanoTime();

        String text = (ticket.getSubject() + " " + ticket.getBody()).toLowerCase();

        CategoryScore category = scoreCategory(text);
        UrgencyScore urgency = scoreUrgency(text);
        double autoResolvableProbability = scoreAutoResolvableProbability(text);

        boolean autoResolvable = aggregator.deriveAutoResolvable(autoResolvableProbability);
        double autoResolvableConfidence = aggregator.deriveAutoResolvableConfidence(autoResolvableProbability, autoResolvable);
        String urgencyLabel = aggregator.mapUrgencyScoreToLabel(urgency.score());

        String action = aggregator.decideAction(autoResolvable, category.confidence(), urgency.confidence(), autoResolvableConfidence);

        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        return new TicketDecision(
                category.category(), category.confidence(),
                urgencyLabel, urgency.confidence(),
                autoResolvable, autoResolvableConfidence,
                action, engineName(), latencyMs
        );
    }

    @Override
    public String engineName() {
        return "rule_based";
    }

    private CategoryScore scoreCategory(String text) {
        String bestCategory = null;
        int bestMatches = 0;

        for (Map.Entry<String, String[]> entry : CATEGORY_KEYWORDS.entrySet()) {
            int matches = countMatches(text, entry.getValue());
            if (matches > bestMatches) {
                bestMatches = matches;
                bestCategory = entry.getKey();
            }
        }

        if (bestCategory == null) {
            return new CategoryScore("how_to", 0.4);
        }
        return new CategoryScore(bestCategory, confidenceFor(bestMatches));
    }

    private UrgencyScore scoreUrgency(String text) {
        int criticalMatches = countMatches(text, CRITICAL_URGENCY_KEYWORDS);
        if (criticalMatches > 0) {
            return new UrgencyScore(3, confidenceFor(criticalMatches));
        }
        int highMatches = countMatches(text, HIGH_URGENCY_KEYWORDS);
        if (highMatches > 0) {
            return new UrgencyScore(2, confidenceFor(highMatches));
        }
        int lowMatches = countMatches(text, LOW_URGENCY_KEYWORDS);
        if (lowMatches > 0) {
            return new UrgencyScore(0, confidenceFor(lowMatches));
        }
        return new UrgencyScore(1, 0.55);
    }

    private double scoreAutoResolvableProbability(String text) {
        int matches = countMatches(text, FAQ_KEYWORDS);
        return matches == 0 ? 0.3 : Math.min(BASE_CONFIDENCE + CONFIDENCE_PER_MATCH * matches, MAX_CONFIDENCE);
    }

    private double confidenceFor(int matches) {
        return Math.min(BASE_CONFIDENCE + CONFIDENCE_PER_MATCH * matches, MAX_CONFIDENCE);
    }

    private int countMatches(String text, String[] keywords) {
        int count = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                count++;
            }
        }
        return count;
    }

    private record CategoryScore(String category, double confidence) {
    }

    private record UrgencyScore(int score, double confidence) {
    }
}
