-- Calendar rows keep the words from the official file. A DATE column cannot
-- store "May 2026" or "April-May,2024", and parsing those into a day would invent one.
ALTER TABLE catalog.exam_series
  ADD COLUMN IF NOT EXISTS expected_notify_text TEXT NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS expected_apply_text TEXT NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS expected_exam_text TEXT NOT NULL DEFAULT '';
