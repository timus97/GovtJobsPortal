ALTER TABLE catalog.opportunities
  ADD COLUMN IF NOT EXISTS source_id TEXT NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS source_url TEXT NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS vacancies TEXT NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS notification_date DATE;

ALTER TABLE catalog.exam_series
  ADD COLUMN IF NOT EXISTS source_id TEXT NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS min_education TEXT NOT NULL DEFAULT '';

UPDATE collect.priority_links
SET url = 'https://www.rrbapply.gov.in/'
WHERE url = 'https://www.rrbcdg.gov.in/'
  AND NOT EXISTS (
    SELECT 1 FROM collect.priority_links WHERE url = 'https://www.rrbapply.gov.in/'
  );

DELETE FROM collect.priority_links WHERE url = 'https://www.rrbcdg.gov.in/';
