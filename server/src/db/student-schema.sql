CREATE TABLE IF NOT EXISTS students (
  id            TEXT PRIMARY KEY,
  email         TEXT NOT NULL,
  email_norm    TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL,
  last_login_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS student_profiles (
  student_id TEXT PRIMARY KEY REFERENCES students(id) ON DELETE CASCADE,
  profile    JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS desk_items (
  id           TEXT PRIMARY KEY,
  student_id   TEXT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  kind         TEXT NOT NULL,
  ref_id       TEXT,
  title        TEXT NOT NULL,
  board        TEXT NOT NULL DEFAULT '',
  status       TEXT NOT NULL,
  exam_date    DATE,
  last_date    DATE,
  official_url TEXT NOT NULL DEFAULT '',
  notes        TEXT NOT NULL DEFAULT '',
  created_at   TIMESTAMPTZ NOT NULL,
  updated_at   TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS desk_items_unique_ref
  ON desk_items (student_id, kind, ref_id)
  WHERE ref_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS desk_items_student_idx ON desk_items (student_id);

CREATE TABLE IF NOT EXISTS desk_files (
  id            TEXT PRIMARY KEY,
  item_id       TEXT NOT NULL REFERENCES desk_items(id) ON DELETE CASCADE,
  kind          TEXT NOT NULL CHECK (kind IN ('admit', 'result')),
  stored_path   TEXT NOT NULL,
  mime          TEXT NOT NULL,
  bytes         INTEGER NOT NULL,
  original_name TEXT NOT NULL,
  uploaded_at   TIMESTAMPTZ NOT NULL,
  UNIQUE (item_id, kind)
);

CREATE TABLE IF NOT EXISTS topic_progress (
  student_id TEXT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  series_id  TEXT NOT NULL,
  topic_id   TEXT NOT NULL,
  done_at    TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (student_id, series_id, topic_id)
);

CREATE TABLE IF NOT EXISTS mock_attempts (
  id           TEXT PRIMARY KEY,
  student_id   TEXT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  series_id    TEXT NOT NULL,
  item_id      TEXT REFERENCES desk_items(id) ON DELETE SET NULL,
  started_at   TIMESTAMPTZ NOT NULL,
  submitted_at TIMESTAMPTZ,
  score        INTEGER,
  total        INTEGER,
  answers      JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS mock_attempts_student_idx ON mock_attempts (student_id, series_id);
