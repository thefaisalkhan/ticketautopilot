# Ticket Triage Copilot

Support-ticket auto-classifier. Primary decision engine is Jev
(`~typesafe/jev-latest`) via OpenRouter, with a rule-based engine as
fallback/baseline and automatic cost-routing fast path. See
`DecisionEngine` for the pluggable interface.

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
   OPENROUTER_API_KEY=... mvn spring-boot:run
   ```
3. Open `http://localhost:8080/`.

The app reads DB connection info from `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`
(see `application.yml` for defaults matching `docker-compose.yml`).

## Config

- `OPENROUTER_API_KEY` — API key for calling Jev via OpenRouter.
- `OPENROUTER_BASE_URL` — defaults to OpenRouter's alpha decisions endpoint
  (`https://openrouter.ai/api/alpha/decisions`).

Jev is a "decisions" model, not a chat model — it's called at
`POST /api/alpha/decisions`, not `/chat/completions`, and the model id
needs a leading `~` (`~typesafe/jev-latest`). Confirmed by a manual test
call; see `JevDecisionEngine` for the exact request/response shape
(`answers.category.choice`, `answers.urgency.score` as a continuous float,
`answers.is_auto_resolvable.noul`).

Jev's Choice and Score questions also return a `probabilities` field — the
full distribution across all categories, or all four urgency levels, not
just the winning value. This is parsed, persisted (`category_probabilities`
/ `urgency_probabilities`, `jsonb` columns), returned in the API, and shown
as a hover tooltip on the dashboard's category/urgency cells — Jev's cells
only, since the rule-based engine has no real distribution and doesn't
fabricate one.

## Engine behavior

- **Jev** (primary) — one call per ticket answering three typed questions
  (category/urgency/auto-resolvable). If the call fails, `TriageService`
  automatically falls back to the rule-based engine, labels the decision
  `engine_used=fallback`, and forces `action=needs_human_review`
  regardless of confidence.
- **Cost-routing fast path** — a small hardcoded FAQ phrase list
  (`FaqFastPathMatcher`) skips Jev entirely for obvious matches (e.g.
  "reset password"), routing straight to the rule-based engine with
  `engine_used=rule_based_fast_path`.
- **Rule-based** — keyword/regex baseline, selectable directly in the
  dashboard, also used for fallback and the fast path.
- **Compare both** — `POST /api/tickets/compare` runs one ticket through
  both engines and persists two decision rows against the same ticket.

`TicketDecisionAggregator` holds the shared thresholding logic (all three
confidences must exceed 0.75 *and* auto_resolvable must be true to
auto-route) so it's identical across engines and not delegated to Jev.

## Importing real tickets

The dashboard's "Import CSV" button (next to Bulk demo mode) parses a CSV
with `Subject` and `Description`/`Body` columns — the same shape a standard
Freshdesk ticket export uses — and feeds each row through the currently
selected engine mode. No backend endpoint; parsing happens client-side.

## Analytics

`analytics.html` (linked from the dashboard header) is a historical view
over every logged decision, backed by `GET /api/analytics/summary`:
category/urgency/engine distributions, avg latency per engine, decisions
per day, and the Jev/rule-based agreement rate — computed only from tickets
that have been triaged by both engines on the same ticket (i.e. via
Compare-both; fallback/fast-path rows don't count as a second opinion,
since they're the rule-based engine relabeled, not an independent one).
Charts are inline SVG bar charts, no charting library.

## Build order

1. ~~Scaffold Spring Boot project + Postgres schema/migrations~~
2. ~~RuleBasedEngine + TicketDecisionAggregator + dashboard, running
   end-to-end on fake/rule-based data~~
3. ~~Manual test call to Jev (via OpenRouter) to confirm request/response
   field names~~
4. ~~JevDecisionEngine wired behind `DecisionEngine`, validated on real
   tickets~~
5. ~~Automatic fallback, cost-routing fast path, engine toggle,
   compare-both view~~

All steps complete.
