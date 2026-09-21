# Kissan Voice Platform — 24-Hour MVP Roadmap

**Goal:** a Spring Boot / Kafka / n8n backend + integration layer that stands on its
own as portfolio evidence for a backend-and-integration role (Java, Spring Boot,
n8n, REST, Kafka, AWS, PostgreSQL).

**Constraint:** one 24-hour window. That is roughly **14–16 productive hours** once
sleep, food and debugging friction are subtracted. Everything below is sized against
16 hours, with a hard cutline at hour 6:30 and a cut list at the end. Plan to hit the
cutline, not the finish line — a small system that runs beats a large one that does not.

---

## 0. On the "built from scratch" framing

You asked for this to look like a from-scratch build rather than a Django conversion.
Worth separating two things, because one of them is fine and the other is not:

- **The Java system genuinely is built from scratch.** New language, new schema (the
  Excel files become real tables), new architecture (event-driven, not request-response
  CRUD), new capabilities (Kafka, S3, CRM sync, n8n) that the Django app never had.
  Saying *"I built an event-driven Spring Boot platform for collecting a voice corpus"*
  is simply true. No one is owed a footnote.
- **Denying the prototype if asked, or backdating commits to manufacture a history,
  is a different thing** — and it is the kind of thing that unravels badly in a
  technical interview, where the question *"why did you make that choice?"* comes up
  constantly. If the honest answer is "because the Python version did it with pandas",
  you want to be able to say so.

There is also a practical argument against hiding it. The role is **system-to-system
integration and migration** — connecting CRM, sales and marketing systems to a
platform. *"I had a Django prototype that stored a corpus in Excel files; I rebuilt it
as a Spring Boot service with a Postgres schema, an outbox, a Kafka event stream and a
CRM integration facade, and here is the migration path I used"* is a **stronger** story
for this specific job than a greenfield toy app. It demonstrates exactly the work they
are hiring for. Greenfield side projects are common; a reasoned migration with a
before/after is not.

**Recommendation:** keep the repo structurally clean — `main` contains only the Java
platform and starts from its own initial commit, which is what is already set up — and
keep the Django prototype on `legacy/django-prototype` where it is preserved but out of
the way. Lead with the platform. Mention the prototype when it strengthens the answer.
That way nothing is fabricated and nothing is buried.

If you disagree and want the legacy branch gone entirely, that is your call — say so
and it will not be pushed.

---

## 1. Repo and branches (already done)

`H:\Kissan-Voice` is now a git repository:

```
legacy/django-prototype   1 commit — the original AgriVoice Django app, preserved
main                      orphan branch, clean history — the Java platform
feat/spring-boot-platform working branch, cut from main
origin                    git@github.com:arslan-sb/Kissan-Voicee.git
```

### Two things to know before you push

1. **That remote is not empty and has nothing to do with this project.** It currently
   holds a Django `notes_backend` app on branches `main` and `InitDev`, three commits
   from December 2024. Pushing this `main` will be rejected as a non-fast-forward.
   Your options:
   - **(Recommended)** Create a fresh repo, e.g. `kissan-voice-platform`, and point
     `origin` at it. A portfolio repo whose history starts with an unrelated notes API
     is worse than a clean one.
   - Push only the feature branch: `git push -u origin feat/spring-boot-platform`.
   - Overwrite: `git push --force origin main`. Destroys the notes_backend history.
2. **You must push from your own Windows terminal, not from here.** This session's
   shell has no SSH access to github.com, so it cannot authenticate with your key.

```powershell
cd H:\Kissan-Voice
git remote -v                      # confirm origin
git push -u origin feat/spring-boot-platform
git push origin legacy/django-prototype   # optional
```

### Working-tree note

`AgriVoice/` and `requirements.txt` still sit on disk but are untracked and gitignored
on `main`. They are safely committed on `legacy/django-prototype`. Once you have pushed
that branch and verified it, delete the folders from Windows Explorer if you want a
clean directory.

---

## 2. What exists today (the thing being replaced)

The Django app is ~860 lines and has **no database at all**. Worth knowing precisely,
because every weakness below becomes a talking point about what you fixed.

| Concern | Django prototype | Consequence |
|---|---|---|
| Storage | One `.xlsx` per user, read/written on every request via pandas | No concurrency safety, no transactions, no indexes, O(n) reads |
| Question corpus | `media/global_questions.xlsx` — 175 Urdu questions with a `Category` column | Reparsed from disk on every request |
| "Which question next?" | `pd.concat` + `drop_duplicates(keep=False)` + `.sample(1)` | A set-difference implemented as a dataframe trick; silently wrong if a question text repeats |
| Media | Audio written to `media/<username>/` on the app server's local disk | Not durable, not scalable, not shareable |
| Identity | A lowercase username in a session cookie, no password | No authentication at all |
| Delete | `os.remove()` on the last row of the spreadsheet | Destructive, unauditable, no soft delete |
| API | 3 endpoints returning ad-hoc `JsonResponse` dicts | No contract, no validation, no error model |
| Integration | None | — |
| Tests | None | — |

**Endpoint mapping to carry over:**

| Django | New REST endpoint |
|---|---|
| `GET /` (username form) | `POST /api/v1/contributors` |
| `GET /questions/<username>/` | `GET /api/v1/contributors/{id}/next-question` |
| `GET /get_next_question/<username>/` | same endpoint (the duplication disappears) |
| `POST /save_recording/<username>/` | `POST /api/v1/contributors/{id}/recordings` |
| `POST /delete_recording/<username>/` | `DELETE /api/v1/recordings/{id}` |

---

## 3. Target architecture

```
                    ┌─────────────────────────────────────────────┐
  Browser / PWA ───▶│  kissan-voice-api   (Spring Boot, Java 21)  │
  (recorder UI)     │                                             │
                    │  contributor │ corpus │ recording │ media   │
                    │  ─────────────────────────────────────────  │
                    │  outbox  │  integration facade              │
                    └───┬───────────┬──────────────┬──────────────┘
                        │           │              │
                 ┌──────▼─────┐ ┌───▼────────┐ ┌───▼──────────────┐
                 │ PostgreSQL │ │  S3         │ │ Kafka (Redpanda) │
                 │  + Flyway  │ │ (LocalStack)│ │  3 topics + DLT  │
                 └────────────┘ └─────────────┘ └───┬──────────────┘
                                                    │
                          ┌─────────────────────────┴──────────┐
                          │                                    │
                 ┌────────▼─────────┐              ┌───────────▼──────────┐
                 │ CRM sync worker  │              │  n8n                 │
                 │ (in-process      │              │  webhook → enrich →  │
                 │  @KafkaListener) │              │  CRM tag + notify    │
                 └────────┬─────────┘              └───────────┬──────────┘
                          │  RestClient + Resilience4j         │
                          │  (retry, circuit breaker,          │
                          │   Idempotency-Key)                 │
                 ┌────────▼────────────────────────────────────▼─────────┐
                 │  WireMock — stands in for "Unite CRM"                 │
                 │  POST /crm/v1/contacts, PATCH /crm/v1/contacts/{id}   │
                 └──────────────────────────────────────────────────────┘
```

**Deliberately a modular monolith, not microservices.** In 24 hours, five services means
five Dockerfiles, five configs and a lot of YAML, and you finish nothing. One deployable
with clean module boundaries and a real event stream demonstrates the same understanding
and actually runs. Say this explicitly in the README — "I chose a modular monolith over
microservices because the operational cost was not justified at this scale" is a senior
answer, not an excuse.

---

## 4. Stack

| Layer | Choice | Why |
|---|---|---|
| Language | **Java 21 (LTS)** | Records, pattern matching, virtual threads |
| Framework | **Spring Boot 3.5.x** (latest patch) | Maximum ecosystem compatibility for a 24h build. Spring Boot 4.0.x is GA and looks more current on a CV — but check `springdoc-openapi`, `spring-kafka` and Testcontainers support before committing to it. **Do not spend MVP hours debugging a version mismatch.** |
| Build | **Maven** + wrapper (`./mvnw`) | The JD's world is Maven-shaped; wrapper means the reviewer needs no local Maven |
| DB | **PostgreSQL 17** + **Flyway** | Versioned migrations are half the point |
| Persistence | Spring Data JPA; JDBC for the outbox poll | |
| API | Spring MVC, `springdoc-openapi` → Swagger UI | A browsable contract is what a reviewer clicks first |
| Errors | RFC 9457 `ProblemDetail` + `@RestControllerAdvice` | |
| Auth | Spring Security, stateless JWT (HS256, self-issued) | Enough to show you know the difference from a session cookie |
| Messaging | **Redpanda** (Kafka API, single container, no ZooKeeper) + `spring-kafka` | Same client code as MSK; boots in seconds |
| Object store | **AWS SDK v2 S3** against **LocalStack** | Real AWS SDK code, zero cost |
| Resilience | **Resilience4j** retry + circuit breaker | The single most JD-relevant library here |
| Automation | **n8n** (Docker), workflows exported as JSON into the repo | |
| Observability | Actuator, Micrometer, `/actuator/prometheus` | |
| Tests | JUnit 5, **Testcontainers** (Postgres + Kafka), MockMvc, WireMock | |
| CI | GitHub Actions: `mvn verify` on push | A green badge on the README does real work |

> **Oracle** is in the JD and is not worth an MVP hour. Address it in the README:
> "All persistence goes through Spring Data JPA with Flyway migrations; the Oracle
> dialect is a profile and a second migration path." That is the accurate answer anyway.

---

## 5. The 24-hour plan

Times are **elapsed working hours**, not clock time. Each block ends with a commit.
"Done when" is the test — if you cannot demonstrate it, do not move on.

### Block 0 — Scaffold and infrastructure · 0:00 → 0:45

- `start.spring.io`: Java 21, Maven, deps — Web, Data JPA, Validation, PostgreSQL
  Driver, Flyway, Kafka, Security, Actuator, Lombok, Testcontainers, Docker Compose.
- Add `springdoc-openapi-starter-webmvc-ui`, `awssdk:s3`, `resilience4j-spring-boot3`.
- Package layout:
  ```
  com.kissanvoice
  ├── common/        config, security, error handling, ids
  ├── contributor/   api · domain · persistence
  ├── corpus/        questions, categories, next-question strategy
  ├── recording/     sessions, uploads, lifecycle
  ├── media/         S3 port + adapter
  ├── outbox/        outbox table, poller, publisher
  └── integration/   CrmPort, CRM adapter, Kafka listeners, n8n webhook
  ```
  Enforce the boundaries with **ArchUnit** if you have a spare 15 minutes — it is a
  strong signal and costs one test class.
- `infra/docker-compose.yml`: postgres, redpanda, redpanda-console, localstack, wiremock, n8n.
- **Done when:** `docker compose up -d` is green and `GET /actuator/health` returns `UP`.

### Block 1 — Persistence and the corpus · 0:45 → 2:30

- Flyway `V1__baseline.sql` (schema in §6).
- `V2__seed_questions.sql` — generate it from the existing workbook:
  ```python
  # tools/seed_questions.py  (run once, commit the generated SQL)
  import openpyxl, uuid
  ws = openpyxl.load_workbook("AgriVoice/media/global_questions.xlsx").active
  rows = [r for r in ws.iter_rows(min_row=2, values_only=True) if r[0]]
  with open("src/main/resources/db/migration/V2__seed_questions.sql", "w",
            encoding="utf-8") as f:
      f.write("INSERT INTO question (id, text, category, language, active) VALUES\n")
      vals = [f"('{uuid.uuid4()}', $q${r[0].strip()}$q$, "
              f"{'NULL' if not r[2] else f'$q${r[2].strip()}$q$'}, 'ur', TRUE)"
              for r in rows]
      f.write(",\n".join(vals) + ";\n")
  ```
  175 questions, Urdu text, with categories. Dollar-quoting avoids escaping pain.
- JPA entities + repositories. Use UUIDv7 for ids if you can (`uuid-creator`), else v4.
- **Done when:** the app boots, Flyway applies both migrations, and
  `SELECT count(*) FROM question` returns 175.

### Block 2 — Core REST API · 2:30 → 5:00

The largest block. Build it in this order:

1. `POST /api/v1/contributors` — register, returns id + JWT.
2. `GET /api/v1/contributors/{id}/next-question` — **this is the interesting one.**
   Replace the pandas `drop_duplicates(keep=False)` trick with a real query:
   ```sql
   SELECT q.* FROM question q
   WHERE q.active
     AND NOT EXISTS (
       SELECT 1 FROM recording r
       WHERE r.question_id = q.id
         AND r.contributor_id = :contributorId
         AND r.status = 'ACCEPTED')
   ORDER BY random() LIMIT 1;
   ```
   Returns `204 No Content` when the corpus is complete — the `completed.html` case.
   Put the strategy behind a `NextQuestionStrategy` interface with a `RandomUnanswered`
   implementation; a second `CoverageWeighted` implementation is a 20-line stretch goal
   that makes the design look intentional.
3. `POST /api/v1/contributors/{id}/recordings` (multipart) — stub the media write for now.
4. `DELETE /api/v1/recordings/{id}` — **soft delete** (`status = 'DELETED'`), not `os.remove`.
5. `GET /api/v1/contributors/{id}/progress` — answered / remaining / percent.
6. Bean Validation on every request DTO; `@RestControllerAdvice` → `ProblemDetail`.
7. Swagger annotations good enough that the UI is self-explanatory.

- **Done when:** you can register, pull a question, post a recording, delete it, and pull
  the same question again — entirely from Swagger UI.

### Block 3 — Media to S3 · 5:00 → 6:30

- `MediaStoragePort` interface; `S3MediaStorage` adapter using AWS SDK v2.
- LocalStack endpoint override via profile; bucket created by an init script.
- Key scheme: `recordings/{contributorId}/{questionId}/{recordingId}.webm`.
- Validate content-type and size; reject > 10 MB with a `ProblemDetail`.
- `GET /api/v1/recordings/{id}/audio` returns a **presigned URL**, not a byte stream.
- **Done when:** upload via Swagger, then `awslocal s3 ls --recursive s3://kissan-voice-media`
  shows the object, and the presigned URL plays in a browser.

> ### ▸▸ CUTLINE — hour 6:30 ◂◂
> At this point the Django app is fully replaced by a Spring Boot + PostgreSQL + S3
> REST service with an OpenAPI contract. **Commit, push, and write the README now,**
> not at hour 23. If everything after this fails, you still have a finished,
> demonstrable project. Everything beyond this point is upside.

### Block 4 — Transactional outbox → Kafka · 6:30 → 8:30

- `outbox_event` table written **in the same transaction** as the domain change.
- `@Scheduled(fixedDelay=500)` poller with `SELECT ... FOR UPDATE SKIP LOCKED`,
  publishes to Kafka, marks `published_at`.
- Topics and payloads in §7. JSON, with a `schemaVersion` field.
- Redpanda Console on `:8085` so you can **screenshot messages flowing** — this
  screenshot is the single most useful image in your README.
- **Done when:** posting a recording produces a `RecordingCaptured` message visible in
  the console, and killing Kafka does not fail the HTTP request (that is the whole point
  of the outbox — say so in the README).

### Block 5 — CRM integration facade · 8:30 → 11:00

**The most JD-relevant block. If you must choose between this and n8n, choose this.**

- WireMock as a stand-in CRM, stubs committed under `infra/wiremock/mappings/`:
  `POST /crm/v1/contacts`, `PATCH /crm/v1/contacts/{id}/activity`, plus one stub that
  returns `500` and one that returns `429` so the resilience behaviour is demonstrable.
- `CrmPort` (outbound port) ← `WireMockCrmAdapter` (Spring `RestClient`). The port/adapter
  split is the "reusable building block" the JD asks about: a second CRM means a second
  adapter and no change to the domain.
- `@KafkaListener` on `kissan.contributor.v1` and `kissan.recording.v1`:
  - `ContributorRegistered` → upsert a CRM contact
  - `RecordingCaptured` → append a CRM activity
- Reliability, and call all four out in the README:
  - **Resilience4j** `@Retry` with exponential backoff + `@CircuitBreaker`
  - **`Idempotency-Key`** header derived from the event id
  - **Consumer-side dedupe** via a `processed_message` table
  - **Dead-letter topic** via `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`
- **Done when:** with the CRM stub forced to 500, messages retry, the breaker opens, the
  message lands on `...dlt`, and the API stays responsive throughout.

### Block 6 — n8n automation · 11:00 → 12:30

Two workflows, exported to `integration/n8n/workflows/*.json` and committed.

1. **Contributor milestone** — the platform `POST`s to an n8n webhook when a contributor
   crosses 25 accepted recordings. n8n: validate → enrich (`GET` contributor from the
   API) → branch → tag the contact in the CRM stub + post to a mock notification webhook.
2. **Nightly corpus report** — n8n Schedule trigger → `GET /api/v1/reports/corpus-coverage`
   → format a summary → write to a file / post to the mock webhook.

Screenshot both canvases for the README. A picture of an n8n workflow is instantly legible
to a non-technical recruiter in a way that Java code is not — this is disproportionately
valuable per hour spent.

- **Done when:** a 25th recording visibly fires the workflow end-to-end.

### Block 7 — Tests and CI · 12:30 → 14:00

Do not chase coverage. Write the four tests that prove the system works:

1. **Testcontainers integration test** — Postgres + Redpanda; register → next-question →
   upload → assert the outbox row and the published Kafka message.
2. **`NextQuestionStrategy` unit test** — never returns an already-answered question;
   returns empty when the corpus is exhausted. (This is the bug class the pandas version
   had. Say so in the test name.)
3. **WireMock resilience test** — 500 then 200 proves the retry; repeated 500 proves the DLT.
4. **MockMvc slice test** — validation failure returns a well-formed `ProblemDetail`.

GitHub Actions: `setup-java@v4` (temurin 21), Maven cache, `./mvnw -B verify`, badge in README.

- **Done when:** CI is green on GitHub and the badge renders.

### Block 8 — Demo client · 14:00 → 15:00

Port `questions2.html` to a **single static page** (plain JS, `MediaRecorder`, `fetch`)
that talks to the REST API with a JWT. Keep the Urdu RTL styling and the Noto Nastaliq
font — it is visually distinctive and makes the demo memorable. Serve it from
`src/main/resources/static/`.

Cut this block first if you are behind. Swagger UI is an acceptable demo surface.

### Block 9 — README, diagram, demo script · 15:00 → 16:30

This block is **not optional** and is worth more per minute than any code block. Most
reviewers spend 90 seconds on a repo and never clone it.

- Architecture diagram (Mermaid renders natively on GitHub — no image hosting).
- The sequence diagram for `recording captured → outbox → Kafka → CRM sync`.
- Screenshots: Swagger UI, Redpanda Console with messages, n8n canvas, green CI.
- **"Design decisions"** section — modular monolith over microservices, outbox over
  dual-write, ports/adapters for the CRM, soft delete over hard delete, presigned URLs
  over streaming. One paragraph each, with the trade-off named.
- A 60-second `DEMO.md`: exact commands from `git clone` to a recording in the CRM stub.
- A `.http` or Bruno collection so a reviewer can fire requests without Swagger.

### Buffer · 16:30 → 18:00

Something will break. Postgres auth, LocalStack region config, Kafka listener
deserialisation — assume 90 minutes of it.

---

## 6. Schema

```sql
CREATE TABLE contributor (
    id            UUID PRIMARY KEY,
    display_name  VARCHAR(120) NOT NULL,
    phone         VARCHAR(32),
    locale        VARCHAR(16)  NOT NULL DEFAULT 'ur-PK',
    status        VARCHAR(24)  NOT NULL DEFAULT 'ACTIVE',
    crm_contact_id VARCHAR(64),                      -- filled by the CRM sync
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_contributor_phone UNIQUE (phone)
);

CREATE TABLE question (
    id          UUID PRIMARY KEY,
    text        TEXT        NOT NULL,
    category    VARCHAR(255),
    subcategory VARCHAR(255),
    language    VARCHAR(16) NOT NULL DEFAULT 'ur',
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_question_active ON question (active) WHERE active;

CREATE TABLE recording_session (
    id             UUID PRIMARY KEY,
    contributor_id UUID NOT NULL REFERENCES contributor(id),
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at   TIMESTAMPTZ,
    status         VARCHAR(24) NOT NULL DEFAULT 'OPEN'
);

CREATE TABLE recording (
    id             UUID PRIMARY KEY,
    session_id     UUID NOT NULL REFERENCES recording_session(id),
    contributor_id UUID NOT NULL REFERENCES contributor(id),
    question_id    UUID NOT NULL REFERENCES question(id),
    media_key      VARCHAR(512) NOT NULL,
    content_type   VARCHAR(64)  NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    duration_ms    INTEGER,
    status         VARCHAR(24)  NOT NULL DEFAULT 'ACCEPTED',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- The dedupe that pandas drop_duplicates() was faking, as a real constraint:
CREATE UNIQUE INDEX uq_recording_answer
    ON recording (contributor_id, question_id)
    WHERE status = 'ACCEPTED';

CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(64)  NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempts       INT          NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_unpublished
    ON outbox_event (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_message (           -- consumer-side idempotency
    message_id   UUID        NOT NULL,
    consumer     VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, consumer)
);
```

---

## 7. Topics and events

| Topic | Events | Partition key |
|---|---|---|
| `kissan.contributor.v1` | `ContributorRegistered`, `ContributorUpdated` | `contributorId` |
| `kissan.recording.v1` | `RecordingCaptured`, `RecordingDeleted` | `contributorId` |
| `kissan.milestone.v1` | `ContributorMilestoneReached` | `contributorId` |
| `kissan.recording.v1.dlt` | failed consumer records | — |

Keying by `contributorId` gives per-contributor ordering — a detail worth one README
sentence, because it shows you thought about partitioning rather than accepting defaults.

```json
{
  "eventId": "018f...",
  "eventType": "RecordingCaptured",
  "schemaVersion": 1,
  "occurredAt": "2026-09-21T10:15:30Z",
  "aggregateId": "018f...",
  "data": {
    "recordingId": "018f...",
    "contributorId": "018f...",
    "questionId": "018f...",
    "category": "کپاس کی اگیتی کاشت",
    "mediaKey": "recordings/018f.../018f.../018f....webm",
    "durationMs": 8400
  }
}
```

---

## 8. Cut list, in the order to cut

If you are behind schedule, drop in this order. Each line costs the least value per hour saved.

1. **Demo client (Block 8)** — Swagger UI demos fine. Saves 1h.
2. **JWT auth** — an `X-Contributor-Id` header with a note "auth is out of MVP scope,
   see §Next steps". Saves 45m. Be explicit about it; an unremarked missing auth layer
   reads as an oversight, a remarked one reads as scoping.
3. **Second n8n workflow** — one is enough to prove the skill. Saves 30m.
4. **`CoverageWeighted` strategy** — the interface alone shows the design. Saves 20m.
5. **ArchUnit** — nice, not load-bearing. Saves 20m.
6. **Circuit breaker** — keep the retry, drop the breaker. Saves 30m.
7. **Milestone topic** — have the API call the n8n webhook directly. Saves 30m.

**Never cut:** the outbox, the CRM adapter, one Testcontainers test, CI, the README.
Those five are what make a reviewer believe the rest.

---

## 9. Definition of done

A reviewer clones the repo and, in under five minutes:

- [ ] `docker compose up -d && ./mvnw spring-boot:run` starts everything
- [ ] Swagger UI at `/swagger-ui.html` lists a coherent, versioned API
- [ ] Registering and uploading produces an object in S3 and a message in Redpanda Console
- [ ] The CRM stub receives a contact and an activity; forcing it to 500 shows retry → DLT
- [ ] The n8n canvas shows a workflow that has actually run
- [ ] CI is green
- [ ] The README explains what was built, why each choice was made, and what would come next

---

## 10. Next steps (write these in the README — the gaps are yours to name)

- OAuth2 / OIDC via Keycloak instead of self-issued JWTs
- Avro or Protobuf with a schema registry instead of JSON events
- Terraform for the AWS footprint (ECS Fargate, RDS, S3, MSK/SQS) + GitHub Actions CD
- An Oracle profile and a second Flyway migration path
- Speech-to-text enrichment so the corpus is searchable
- Per-contributor rate limiting and virus scanning on upload

---

## 11. Interview talking points

Have one crisp sentence ready for each:

1. **Why an outbox and not a direct publish?** Dual writes are not atomic; a crash
   between the DB commit and the Kafka send loses the event silently.
2. **Why is the CRM behind a port?** A second CRM is a new adapter, not a change to the
   domain. That is what makes integrations "faster to implement".
3. **Why a modular monolith?** The operational cost of five services was not justified
   at this scale; the module boundaries mean extraction stays cheap if it ever is.
4. **Why key by contributor?** Ordering guarantees are per-partition, and the ordering
   that matters is per-contributor.
5. **Why both retry and consumer-side dedupe?** At-least-once delivery means the consumer
   must be idempotent regardless of how careful the producer is.
6. **Where would it break at 100× scale?** `ORDER BY random()` over the question table,
   and the single-threaded outbox poller. Naming your own bottleneck unprompted is the
   strongest signal in this entire list.
