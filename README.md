# Ticket Triage Copilot

Support-ticket auto-classifier. Primary decision engine is Jev
(`typesafe-ai/jev`) via Vercel AI Gateway, with a rule-based engine as
fallback/baseline. See `DecisionEngine` for the pluggable interface.

## Stack

- Backend: Spring Boot 3 (Java 21), Maven
- Database: Postgres, schema managed by Flyway (`src/main/resources/db/migration`)
- Frontend: static HTML/JS dashboard (added in a later build step)

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

- `VERCEL_AI_GATEWAY_API_KEY` — API key for calling Jev via Vercel AI Gateway
  (wired in once `JevDecisionEngine` is built).
- `VERCEL_AI_GATEWAY_BASE_URL` — base URL for the gateway's evaluate-style API.

## Build order

1. ~~Scaffold Spring Boot project + Postgres schema/migrations~~ (this step)
2. RuleBasedEngine + TicketDecisionAggregator + dashboard, running end-to-end
   on fake/rule-based data
3. Manual test call to Jev to confirm request/response field names
4. JevDecisionEngine wired behind `DecisionEngine`, validated on real tickets
5. Automatic fallback, cost-routing fast path, engine toggle, compare-both view
