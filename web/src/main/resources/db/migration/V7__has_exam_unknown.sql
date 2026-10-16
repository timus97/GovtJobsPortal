-- A notice that never said whether there is a written exam stays unknown.
-- FALSE is only for a notice that says there is no written exam.
ALTER TABLE catalog.opportunities
  ALTER COLUMN has_exam DROP NOT NULL;
