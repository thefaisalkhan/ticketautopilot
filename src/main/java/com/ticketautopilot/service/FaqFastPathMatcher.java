package com.ticketautopilot.service;

import com.ticketautopilot.domain.Ticket;
import org.springframework.stereotype.Component;

/**
 * Cost-routing fast path: a small, fixed list of unambiguous FAQ phrases
 * that skip Jev entirely and go straight to RuleBasedEngine, since paying
 * for a model call to look up "how do I reset my password" isn't worth it.
 */
@Component
public class FaqFastPathMatcher {

    private static final String[] FAQ_PHRASES = {
            "reset password", "reset my password", "forgot my password",
            "cancel subscription", "cancel my subscription"
    };

    public boolean matches(Ticket ticket) {
        String text = (ticket.getSubject() + " " + ticket.getBody()).toLowerCase();
        for (String phrase : FAQ_PHRASES) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }
}
