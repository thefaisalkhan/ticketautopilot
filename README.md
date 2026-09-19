# Ticket Triage Copilot

Support-ticket auto-classifier. Primary decision engine is Jev
(`typesafe/jev-latest`) via OpenRouter, with a rule-based engine as
fallback/baseline. See `DecisionEngine` for the pluggable interface.

## Stack

- Backend: Spring Boot 3 (Java 21), Maven
- Database: Postgres, schema managed by Flyway (`src/main/resources/db/migration`)
- Frontend: static HTML/JS dashboard (`src/main/resources/static`)

## Local setup

1. Start Postgres:
   ```
   docker compose up -d
   ```
2. Run the app (Flyway migrates the schema on startup):
   ```
   ./mvnw spring-boot:run
   ```
   or `mvn spring-boot:run` if you don't have the wrapper installed.

The app reads DB connection info from `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`
(see `application.yml` for defaults matching `docker-compose.yml`).

## Config

- `OPENROUTER_API_KEY` — API key for calling Jev via OpenRouter
  (wired in once `JevDecisionEngine` is built).
- `OPENROUTER_BASE_URL` — defaults to OpenRouter's standard chat-completions
  endpoint (`https://openrouter.ai/api/v1/chat/completions`).

Note: `typesafe/jev-latest` doesn't appear in OpenRouter's public model
catalog or model-page routes as of this writing — it's early access, so the
model id and the shape of Jev's own request/response fields (as opposed to
OpenRouter's generic chat-completions envelope) still need confirming with
a manual test call before `JevDecisionEngine` is wired in for real.

## Build order

1. ~~Scaffold Spring Boot project + Postgres schema/migrations~~ (done)
2. ~~RuleBasedEngine + TicketDecisionAggregator + dashboard, running
   end-to-end on fake/rule-based data~~ (done)
3. Manual test call to Jev (via OpenRouter) to confirm request/response
   field names
4. JevDecisionEngine wired behind `DecisionEngine`, validated on real tickets
5. Automatic fallback, cost-routing fast path, engine toggle, compare-both view
