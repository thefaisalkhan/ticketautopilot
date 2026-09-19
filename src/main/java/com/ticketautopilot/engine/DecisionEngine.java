package com.ticketautopilot.engine;

import com.ticketautopilot.domain.Ticket;

public interface DecisionEngine {
    TicketDecision evaluate(Ticket ticket);

    String engineName(); // "jev" | "rule_based" | "fallback" | "rule_based_fast_path"
}
