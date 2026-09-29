CREATE SCHEMA IF NOT EXISTS collect;

CREATE TABLE IF NOT EXISTS collect.runs (
  id             TEXT PRIMARY KEY,
  started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at    TIMESTAMPTZ,
  status         TEXT NOT NULL CHECK (status IN ('running', 'finished', 'failed')),
  sources_total  INT NOT NULL DEFAULT 0,
  kept           INT NOT NULL DEFAULT 0,
  review_count   INT NOT NULL DEFAULT 0,
  failed         INT NOT NULL DEFAULT 0,
  pdfs           INT NOT NULL DEFAULT 0,
  started_by     TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS collect.source_results (
  id            TEXT PRIMARY KEY,
  run_id        TEXT NOT NULL REFERENCES collect.runs(id) ON DELETE CASCADE,
  source_name   TEXT NOT NULL,
  url           TEXT NOT NULL,
  phase         TEXT NOT NULL DEFAULT 'fetch',
  outcome       TEXT NOT NULL CHECK (outcome IN ('ok', 'failed')),
  kept          INT NOT NULL DEFAULT 0,
  review_count  INT NOT NULL DEFAULT 0,
  dropped       INT NOT NULL DEFAULT 0,
  error         TEXT NOT NULL DEFAULT '',
  duration_ms   BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS collect.notices (
  id                 TEXT PRIMARY KEY,
  run_id             TEXT NOT NULL REFERENCES collect.runs(id) ON DELETE CASCADE,
  source_result_id   TEXT REFERENCES collect.source_results(id) ON DELETE SET NULL,
  title              TEXT NOT NULL,
  organization       TEXT NOT NULL,
  official_url       TEXT NOT NULL,
  last_date          DATE,
  selection_process  TEXT NOT NULL DEFAULT '',
  has_exam           BOOLEAN NOT NULL DEFAULT FALSE,
  excerpt            TEXT NOT NULL DEFAULT '',
  disposition        TEXT NOT NULL CHECK (disposition IN ('kept', 'review', 'dropped')),
  review_status      TEXT NOT NULL CHECK (review_status IN ('waiting', 'held', 'rejected', 'approved')),
  wait_reason        TEXT NOT NULL DEFAULT '',
  catalog_id         TEXT,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS collect_notices_review_idx ON collect.notices (review_status, created_at DESC);
CREATE INDEX IF NOT EXISTS collect_notices_run_idx ON collect.notices (run_id);

CREATE TABLE IF NOT EXISTS collect.keywords (
  id          TEXT PRIMARY KEY,
  phrase      TEXT NOT NULL UNIQUE,
  sort_order  INT NOT NULL,
  enabled     BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS collect.priority_links (
  id          TEXT PRIMARY KEY,
  url         TEXT NOT NULL UNIQUE,
  label       TEXT NOT NULL DEFAULT '',
  sort_order  INT NOT NULL,
  enabled     BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS collect.job_log (
  id           BIGSERIAL PRIMARY KEY,
  at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  level        TEXT NOT NULL,
  run_id       TEXT,
  source_name  TEXT NOT NULL DEFAULT '',
  phase        TEXT NOT NULL DEFAULT '',
  message      TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS collect_job_log_run_idx ON collect.job_log (run_id, id);

INSERT INTO collect.keywords (id, phrase, sort_order) VALUES
  ('kw-recruitment', 'recruitment', 1),
  ('kw-vacancy', 'vacancy', 2),
  ('kw-notification', 'notification', 3),
  ('kw-walkin', 'walk-in', 4),
  ('kw-advertisement', 'advertisement', 5)
ON CONFLICT (phrase) DO NOTHING;

INSERT INTO collect.priority_links (id, url, label, sort_order) VALUES
  ('link-ssc', 'https://ssc.gov.in/', 'SSC', 1),
  ('link-upsc', 'https://upsc.gov.in/', 'UPSC', 2),
  ('link-ibps', 'https://www.ibps.in/', 'IBPS', 3),
  ('link-rrb', 'https://www.rrbcdg.gov.in/', 'RRB', 4)
ON CONFLICT (url) DO NOTHING;
