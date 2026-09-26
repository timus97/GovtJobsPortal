CREATE SCHEMA IF NOT EXISTS catalog;

CREATE TABLE IF NOT EXISTS catalog.opportunities (
  id                 TEXT PRIMARY KEY,
  title              TEXT NOT NULL,
  organization       TEXT NOT NULL,
  org_type           TEXT NOT NULL DEFAULT '',
  sector             TEXT NOT NULL DEFAULT '',
  location           TEXT NOT NULL DEFAULT '',
  qualification      TEXT NOT NULL DEFAULT '',
  selection_process  TEXT NOT NULL DEFAULT '',
  has_exam           BOOLEAN NOT NULL DEFAULT FALSE,
  last_date          DATE,
  official_url       TEXT NOT NULL,
  source_name        TEXT NOT NULL DEFAULT 'sample',
  review_status      TEXT NOT NULL CHECK (review_status IN ('needs_review', 'approved', 'rejected')),
  summary            TEXT NOT NULL DEFAULT '',
  sample             BOOLEAN NOT NULL DEFAULT FALSE,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS catalog_opportunities_review_idx
  ON catalog.opportunities (review_status);

CREATE TABLE IF NOT EXISTS catalog.exam_series (
  id             TEXT PRIMARY KEY,
  name           TEXT NOT NULL,
  board          TEXT NOT NULL,
  cycle          TEXT NOT NULL DEFAULT '',
  apply_never    BOOLEAN NOT NULL DEFAULT FALSE,
  official_url   TEXT NOT NULL,
  expected_exam  DATE,
  review_status  TEXT NOT NULL CHECK (review_status IN ('needs_review', 'approved', 'rejected')),
  linked_ids     TEXT NOT NULL DEFAULT '',
  summary        TEXT NOT NULL DEFAULT '',
  sample         BOOLEAN NOT NULL DEFAULT FALSE,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
