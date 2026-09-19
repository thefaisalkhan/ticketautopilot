CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Incoming support tickets.
-- v1 ingests tickets synchronously via the REST API (see TicketController).
-- If ticket volume grows, this is where a Kafka consumer would land instead:
-- a producer publishes raw tickets to a topic, and a consumer here persists
-- them and hands off to the decision engine, decoupling ingestion from triage.
CREATE TABLE tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject TEXT NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- One row per triage decision, logging which engine decided what and how confidently.
CREATE TABLE decisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES tickets(id),

    category VARCHAR(50) NOT NULL,
    category_confidence DOUBLE PRECISION NOT NULL,

    urgency VARCHAR(20) NOT NULL,
    urgency_confidence DOUBLE PRECISION NOT NULL,

    auto_resolvable BOOLEAN NOT NULL,
    auto_resolvable_confidence DOUBLE PRECISION NOT NULL,

    action VARCHAR(30) NOT NULL,
    engine_used VARCHAR(30) NOT NULL,

    latency_ms INT NOT NULL,
    decided_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_decisions_ticket_id ON decisions(ticket_id);
CREATE INDEX idx_decisions_decided_at ON decisions(decided_at);
