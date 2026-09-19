# Ticket Triage Copilot

Support-ticket auto-classifier. Primary decision engine is Jev
(`~typesafe/jev-latest`) via OpenRouter, with a rule-based engine as
fallback/baseline and automatic cost-routing fast path. See
`DecisionEngine` for the pluggable interface.

## Stack

- Backend: Spring Boot 3 (Java 21), Maven
- Database: Postgres, schema managed by Flyway (`src/main/resources/db/migration`)
- Frontend: static HTML/JS dashboard (`src/main/resources/static`)

## Architecture

### Request flow

```
Browser (dashboard / analytics)
        │  REST (JSON)
        ▼
TicketController  /  AnalyticsController
        │
        ▼
TriageService ───────────────────────────► TicketRepository ─► tickets (Postgres)
        │  resolves engine, applies
        │  fallback / fast-path routing
        ▼
DecisionEngine  (interface — same TicketDecision shape either way)
   ├── JevDecisionEngine ──► OpenRouter POST /api/alpha/decisions ──► Jev
   └── RuleBasedEngine   ──► keyword/regex scoring, no network call
        │
        ▼
TicketDecisionAggregator  (shared thresholding — never delegated to Jev)
        │
        ▼
DecisionRepository ─► decisions (Postgres, incl. jsonb probability columns)
```

### Components

| Component | Responsibility |
|---|---|
| `TicketController` | `POST /api/tickets`, `POST /api/tickets/compare` |
| `AnalyticsController` | `GET /api/analytics/summary` — historical aggregation |
| `TriageService` | Orchestration: persist the ticket, pick the engine, apply fallback/fast-path routing, persist the decision |
| `DecisionEngine` | The interface both engines implement: `evaluate(Ticket) -> TicketDecision` |
| `JevDecisionEngine` | Calls Jev via OpenRouter, parses its typed answers into a `TicketDecision` |
| `RuleBasedEngine` | Keyword/regex baseline — deterministic, free, no network call |
| `TicketDecisionAggregator` | Shared, engine-agnostic thresholding logic (the auto-route rule) |
| `FaqFastPathMatcher` | Small hardcoded FAQ phrase list — skips Jev entirely for obvious matches |
| `AnalyticsService` | Aggregates the logged `decisions` table for the analytics dashboard |
| `Ticket` / `Decision` | JPA entities — one ticket can have multiple decisions (Compare-both logs one per engine) |

### Design decisions worth knowing

- **`DecisionEngine` is the seam.** Both engines return the exact same
  `TicketDecision` shape, so `TriageService`, the aggregator, the DB schema,
  and the dashboard don't know or care which engine produced a result.
  Adding a third engine means implementing one interface, not touching
  anything else.
- **Thresholding lives in code, never in the model.**
  `TicketDecisionAggregator.decideAction` is the *only* place that decides
  `auto_route` vs `needs_human_review`, applied identically whether the
  confidence numbers came from Jev or the keyword baseline. Jev is never
  asked "should this auto-route?" directly — it only answers three atomic
  questions (category, urgency, auto-resolvable); the routing decision is
  deterministic application code, auditable independent of the model.
- **Fallback and the fast path are routing decisions, not new engines.**
  Both are literally `RuleBasedEngine.evaluate()` with the result relabeled
  (`TicketDecision.withEngineUsed(...)` / `withEngineUsedAndAction(...)`) —
  no duplicated scoring logic anywhere.

## Jev Engine, in detail

Jev (TypeSafe AI's decision model) is **not a chat model**. It's reached
through OpenRouter's alpha **decisions** API — a different endpoint and
payload shape from the standard chat-completions API every other OpenRouter
model uses. None of this was documented anywhere findable; it was
confirmed by a live manual test call before `JevDecisionEngine` was
written for real (see the "Jev integration" PR history).

### The request

```json
POST https://openrouter.ai/api/alpha/decisions
Authorization: Bearer <OPENROUTER_API_KEY>

{
  "model": "~typesafe/jev-latest",
  "state": { "subject": "...", "body": "..." },
  "questions": {
    "category": {
      "type": "choice",
      "instructions": "Which category best fits this ticket?",
      "criteria": {
        "billing": "Charges, invoices, refunds",
        "bug": "Software errors or broken functionality",
        "feature_request": "Request for new functionality",
        "how_to": "Asking how to use something",
        "account": null
      }
    },
    "urgency": {
      "type": "score",
      "instructions": "How urgent is this ticket?",
      "criteria": ["Low — can wait", "Normal", "High — needs prompt attention", "Critical — blocking/urgent"]
    },
    "is_auto_resolvable": {
      "type": "noul",
      "instructions": "Can this be resolved by pointing to an FAQ/help doc without human intervention?"
    }
  }
}
```

Three things that aren't obvious from the field names alone:

- The model id needs a leading **`~`** — `~typesafe/jev-latest`, not
  `typesafe/jev-latest` (the latter 400s with "not a valid model ID").
- `is_auto_resolvable`'s type is **`"noul"`**, not `"boolean"` — Jev's
  schema rejects `"boolean"` outright with a validation error.
- `criteria` differs by question type: a **map** (choice → description,
  `null` allowed) for `category`; an **ordered list** of exactly 4 rubric
  labels for `urgency`.

### The response

```json
{
  "model": "typesafe/jev-1.13-20260917",
  "answers": {
    "category": {
      "choice": "bug",
      "probabilities": { "billing": 0, "bug": 1, "how_to": 0, "feature_request": 0, "account": 0 },
      "confidence": 1
    },
    "urgency": {
      "score": 2.23,
      "probabilities": { "0": 0, "1": 0, "2": 0.76, "3": 0.24 },
      "confidence": 0.76
    },
    "is_auto_resolvable": { "noul": 0.09 }
  }
}
```

Three more things that differ from what the field names suggest:

- `urgency.score` is a **continuous float** (e.g. `2.23`), not a discrete
  `0 | 1 | 2 | 3` — `JevDecisionEngine` rounds and clamps it into range
  before mapping to a label.
- The Noul answer field is literally named **`noul`**, not `probability`.
- Both `category` and `urgency` return a full **`probabilities`**
  distribution across every option, not just the winning one. Parsed,
  persisted (`category_probabilities` / `urgency_probabilities`, `jsonb`
  columns), and shown as a hover tooltip on the dashboard — Jev's cells
  only, since the rule-based engine has no real distribution and doesn't
  fabricate one.

### How the response becomes a decision

`JevDecisionEngine.parseResponse` maps this onto the exact same
`TicketDecision` shape `RuleBasedEngine` produces, so nothing downstream
needs to know which engine ran:

| Jev field | → | `TicketDecision` field |
|---|---|---|
| `answers.category.choice` | → | `category` |
| `answers.category.confidence` | → | `categoryConfidence` |
| `answers.urgency.score` (rounded, clamped to 0–3) | → | `urgency` (via `TicketDecisionAggregator.mapUrgencyScoreToLabel`) |
| `answers.urgency.confidence` | → | `urgencyConfidence` |
| `answers.is_auto_resolvable.noul` | → | `autoResolvable` (`noul > 0.5`) and `autoResolvableConfidence` (`noul` if true, `1 - noul` if false) |
| *(computed, not returned by Jev)* | → | `action` — via `TicketDecisionAggregator.decideAction`, the identical code path the rule-based engine uses |

`action = auto_route` only when **all** of these hold: `autoResolvable ==
true`, and `categoryConfidence`, `urgencyConfidence`, and
`autoResolvableConfidence` each exceed `0.75`. This bar is enforced by
`TicketDecisionAggregator`, not by Jev. A confident **"no"** (high
`autoResolvableConfidence` paired with `autoResolvable == false`) must
never auto-route just because the model was sure about saying no — this
was a real bug the first live Jev calls surfaced (an early version checked
only confidence magnitude, not the boolean value) and is now a permanent
regression test.

### Resilience

- **Automatic fallback** — a failed or malformed Jev response
  (`JevEngineException`) makes `TriageService` fall back to
  `RuleBasedEngine`, relabel `engine_used=fallback`, and force
  `action=needs_human_review` regardless of the rule-based engine's own
  confidence. A degraded Jev never silently routes a ticket wrong.
- **Cost-routing fast path** — a small hardcoded FAQ phrase list
  (`FaqFastPathMatcher`) skips the Jev call entirely for obvious matches
  (e.g. "reset password"), going straight to the rule-based engine with
  `engine_used=rule_based_fast_path`.
- **Compare both** — `POST /api/tickets/compare` runs one ticket through
  both engines and persists two decision rows against the same ticket,
  which is what the analytics agreement-rate calculation relies on.

## Data model

```
tickets                          decisions
─────────                        ──────────────────────────────────────
id (uuid, PK)                    id (uuid, PK)
subject                          ticket_id (FK → tickets.id)
body                             category, category_confidence
created_at                       urgency, urgency_confidence
                                  auto_resolvable, auto_resolvable_confidence
                                  action
                                  engine_used   jev | rule_based |
                                                fallback | rule_based_fast_path
                                  latency_ms
                                  decided_at
                                  category_probabilities  (jsonb, Jev only)
                                  urgency_probabilities   (jsonb, Jev only)
```

One ticket can have multiple decisions on purpose — Compare-both logs one
per engine against the same `ticket_id`, and nothing in the schema
prevents it (no uniqueness constraint on `ticket_id`).

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


