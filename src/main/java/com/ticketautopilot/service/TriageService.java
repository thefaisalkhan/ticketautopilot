package com.ticketautopilot.service;

import com.ticketautopilot.domain.Decision;
import com.ticketautopilot.domain.Ticket;
import com.ticketautopilot.dto.EngineDecisionView;
import com.ticketautopilot.dto.TicketCompareResponse;
import com.ticketautopilot.dto.TicketDecisionResponse;
import com.ticketautopilot.engine.DecisionEngine;
import com.ticketautopilot.engine.JevEngineException;
import com.ticketautopilot.engine.TicketDecision;
import com.ticketautopilot.repository.DecisionRepository;
import com.ticketautopilot.repository.TicketRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TriageService {

    private static final String JEV = "jev";
    private static final String RULE_BASED = "rule_based";

    private final TicketRepository ticketRepository;
    private final DecisionRepository decisionRepository;
    private final Map<String, DecisionEngine> enginesByName;
    private final FaqFastPathMatcher faqFastPathMatcher;

    public TriageService(TicketRepository ticketRepository,
                          DecisionRepository decisionRepository,
                          List<DecisionEngine> engines,
                          FaqFastPathMatcher faqFastPathMatcher) {
        this.ticketRepository = ticketRepository;
        this.decisionRepository = decisionRepository;
        this.enginesByName = engines.stream()
                .collect(Collectors.toMap(DecisionEngine::engineName, Function.identity()));
        this.faqFastPathMatcher = faqFastPathMatcher;
    }

    public TicketDecisionResponse triage(String subject, String body, String engineName) {
        Ticket ticket = ticketRepository.save(new Ticket(subject, body));
        Decision decision = evaluateAndSave(ticket, engineName);
        return TicketDecisionResponse.from(ticket, decision);
    }

    public TicketCompareResponse compare(String subject, String body) {
        Ticket ticket = ticketRepository.save(new Ticket(subject, body));
        Decision jevDecision = evaluateAndSave(ticket, JEV);
        Decision ruleBasedDecision = evaluateAndSave(ticket, RULE_BASED);
        return new TicketCompareResponse(
                ticket.getId(), ticket.getSubject(), ticket.getBody(),
                EngineDecisionView.from(jevDecision), EngineDecisionView.from(ruleBasedDecision)
        );
    }

    private Decision evaluateAndSave(Ticket ticket, String engineName) {
        TicketDecision result = evaluate(ticket, engineName);
        Decision decision = new Decision(
                ticket.getId(),
                result.category(), result.categoryConfidence(),
                result.urgency(), result.urgencyConfidence(),
                result.autoResolvable(), result.autoResolvableConfidence(),
                result.action(), result.engineUsed(),
                (int) result.latencyMs()
        );
        return decisionRepository.save(decision);
    }

    private TicketDecision evaluate(Ticket ticket, String engineName) {
        if (JEV.equals(engineName)) {
            return evaluateJev(ticket);
        }
        DecisionEngine engine = enginesByName.get(engineName);
        if (engine == null) {
            throw new IllegalArgumentException("Unknown engine: " + engineName);
        }
        return engine.evaluate(ticket);
    }

    private TicketDecision evaluateJev(Ticket ticket) {
        if (faqFastPathMatcher.matches(ticket)) {
            return enginesByName.get(RULE_BASED).evaluate(ticket).withEngineUsed("rule_based_fast_path");
        }
        try {
            return enginesByName.get(JEV).evaluate(ticket);
        } catch (JevEngineException e) {
            return enginesByName.get(RULE_BASED).evaluate(ticket)
                    .withEngineUsedAndAction("fallback", "needs_human_review");
        }
    }
}
