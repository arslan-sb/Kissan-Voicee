-- Kissan Voice Platform - baseline schema.
-- Replaces the per-user .xlsx files of the original prototype.

CREATE TABLE contributor (
    id             UUID PRIMARY KEY,
    display_name   VARCHAR(120) NOT NULL,
    phone          VARCHAR(32),
    locale         VARCHAR(16)  NOT NULL DEFAULT 'ur-PK',
    status         VARCHAR(24)  NOT NULL DEFAULT 'ACTIVE',
    crm_contact_id VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_contributor_phone UNIQUE (phone)
);

CREATE TABLE question (
    id          UUID PRIMARY KEY,
    text        TEXT        NOT NULL,
    category    VARCHAR(500),
    subcategory VARCHAR(500),
    language    VARCHAR(16) NOT NULL DEFAULT 'ur',
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_question_active ON question (active) WHERE active;

CREATE TABLE recording_session (
    id             UUID PRIMARY KEY,
    contributor_id UUID        NOT NULL REFERENCES contributor (id),
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at   TIMESTAMPTZ,
    status         VARCHAR(24) NOT NULL DEFAULT 'OPEN'
);
CREATE INDEX idx_session_contributor ON recording_session (contributor_id);

CREATE TABLE recording (
    id             UUID PRIMARY KEY,
    session_id     UUID         NOT NULL REFERENCES recording_session (id),
    contributor_id UUID         NOT NULL REFERENCES contributor (id),
    question_id    UUID         NOT NULL REFERENCES question (id),
    media_key      VARCHAR(512) NOT NULL,
    content_type   VARCHAR(64)  NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    duration_ms    INTEGER,
    status         VARCHAR(24)  NOT NULL DEFAULT 'ACCEPTED',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- The dedupe the prototype faked with pandas drop_duplicates(keep=False),
-- expressed as an actual constraint. A contributor answers a question once.
CREATE UNIQUE INDEX uq_recording_answer
    ON recording (contributor_id, question_id)
    WHERE status = 'ACCEPTED';

CREATE INDEX idx_recording_contributor ON recording (contributor_id);

CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id   UUID        NOT NULL,
    event_type     VARCHAR(64) NOT NULL,
    payload        JSONB       NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempts       INT         NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_unpublished
    ON outbox_event (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_message (
    message_id   UUID        NOT NULL,
    consumer     VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, consumer)
);
