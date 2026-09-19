package com.ticketautopilot.domain;

import com.ticketautopilot.repository.DecisionRepository;
import com.ticketautopilot.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real Postgres jsonb columns end to end: Decision.categoryProbabilities/
 * urgencyProbabilities rely on Hibernate's native JSON mapping (@JdbcTypeCode(SqlTypes.JSON)),
 * which pure unit tests (mocked repositories) can't verify. Needs a running Postgres
 * matching application.yml (see docker-compose.yml) — unlike the rest of the suite,
 * this test hits a real database. Each test flushes and clears the persistence
 * context before reading back, forcing a real round trip through Postgres rather
 * than the first-level cache handing back the same in-memory object.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DecisionRepositoryTest {

    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private DecisionRepository decisionRepository;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void roundTripsProbabilityDistributionsThroughJsonbColumns() {
        Ticket ticket = ticketRepository.save(new Ticket("Overcharged on my invoice", "Please refund the charge."));

        Map<String, Double> categoryProbabilities = Map.of("billing", 1.0, "bug", 0.0, "how_to", 0.0);
        Map<String, Double> urgencyProbabilities = Map.of("low", 0.05, "normal", 0.19, "high", 0.76, "critical", 0.0);

        Decision saved = decisionRepository.save(new Decision(
                ticket.getId(), "billing", 1.0, "high", 0.76, false, 0.54,
                "needs_human_review", "jev", 250,
                categoryProbabilities, urgencyProbabilities
        ));

        entityManager.flush();
        entityManager.clear();

        Decision reloaded = decisionRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getCategoryProbabilities()).containsExactlyInAnyOrderEntriesOf(categoryProbabilities);
        assertThat(reloaded.getUrgencyProbabilities()).containsExactlyInAnyOrderEntriesOf(urgencyProbabilities);
    }

    @Test
    void leavesProbabilityColumnsNullForTheRuleBasedEngine() {
        Ticket ticket = ticketRepository.save(new Ticket("Overcharged on my invoice", "Please refund the charge."));

        Decision saved = decisionRepository.save(new Decision(
                ticket.getId(), "billing", 0.9, "high", 0.9, false, 0.7,
                "needs_human_review", "rule_based", 0,
                null, null
        ));

        entityManager.flush();
        entityManager.clear();

        Decision reloaded = decisionRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getCategoryProbabilities()).isNull();
        assertThat(reloaded.getUrgencyProbabilities()).isNull();
    }
}
