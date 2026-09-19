package com.ticketautopilot.service;

import com.ticketautopilot.domain.Ticket;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FaqFastPathMatcherTest {

    private final FaqFastPathMatcher matcher = new FaqFastPathMatcher();

    @Test
    void matchesResetPasswordPhraseCaseInsensitively() {
        Ticket ticket = new Ticket("Help", "I need to RESET PASSWORD for my account please.");
        assertThat(matcher.matches(ticket)).isTrue();
    }

    @Test
    void matchesCancelSubscriptionPhrase() {
        Ticket ticket = new Ticket("Cancel my subscription", "Please cancel my subscription today.");
        assertThat(matcher.matches(ticket)).isTrue();
    }

    @Test
    void doesNotMatchUnrelatedTickets() {
        Ticket ticket = new Ticket("App crashes on login", "The app crashes every time I log in.");
        assertThat(matcher.matches(ticket)).isFalse();
    }
}
