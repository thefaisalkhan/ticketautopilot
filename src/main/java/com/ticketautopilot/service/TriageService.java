package com.ticketautopilot.service;

import com.ticketautopilot.domain.Decision;
import com.ticketautopilot.domain.Ticket;
import com.ticketautopilot.dto.TicketDecisionResponse;
import com.ticketautopilot.engine.DecisionEngine;
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

    private final TicketRepository ticketRepository;
    private final DecisionRepository decisionRepository;
    private final Map<String, DecisionEngine> enginesByName;

    public TriageService(TicketRepository ticketRepository,
                          DecisionRepository decisionRepository,
                          List<DecisionEngine> engines) {
        this.ticketRepository = ticketRepository;
        this.decisionRepository = decisionRepository;
        this.enginesByName = engines.stream()
                .collect(Collectors.toMap(DecisionEngine::engineName, Function.identity()));
    }

    public TicketDecisionResponse triage(String subject, String body, String engineName) {
        DecisionEngine engine = enginesByName.get(engineName);
        if (engine == null) {
            throw new IllegalArgumentException("Unknown engine: " + engineName);
        }

        Ticket ticket = ticketRepository.save(new Ticket(subject, body));
        TicketDecision result = engine.evaluate(ticket);

        Decision decision = new Decision(
                ticket.getId(),
                result.category(), result.categoryConfidence(),
                result.urgency(), result.urgencyConfidence(),
                result.autoResolvable(), result.autoResolvableConfidence(),
                result.action(), result.engineUsed(),
                (int) result.latencyMs()
        );
        decisionRepository.save(decision);

        return TicketDecisionResponse.from(ticket, decision);
    }
}
