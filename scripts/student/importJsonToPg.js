/**
 * Copy data/students/students.json into Postgres.
 * Requires STUDENT_STORE=postgres and a reachable student-db.
 * Does not move admit/result files — those stay under STUDENT_FILES_DIR.
 */
process.env.STUDENT_STORE = process.env.STUDENT_STORE || 'postgres';

const fs = require('fs');
const path = require('path');
const jsonStore = require('../../server/src/services/studentStoreJson');
const pgStore = require('../../server/src/services/studentStorePg');

async function main() {
  const src = process.env.STUDENT_JSON || jsonStore.storePath();
  if (!fs.existsSync(src)) {
    console.log(`No JSON store at ${src}; nothing to import.`);
    return;
  }
  await pgStore.ready();
  const raw = JSON.parse(fs.readFileSync(src, 'utf8'));
  const students = Array.isArray(raw.students) ? raw.students : [];
  const profiles = raw.profiles && typeof raw.profiles === 'object' ? raw.profiles : {};
  const items = Array.isArray(raw.items) ? raw.items : [];
  const files = Array.isArray(raw.files) ? raw.files : [];
  const topics = Array.isArray(raw.topicProgress) ? raw.topicProgress : [];
  const attempts = Array.isArray(raw.mockAttempts) ? raw.mockAttempts : [];

  let imported = { students: 0, profiles: 0, items: 0, files: 0, topics: 0, attempts: 0 };

  for (const st of students) {
    const existing = await pgStore.findByEmail(st.email || st.emailNorm);
    if (existing) continue;
    const pg = require('../../server/src/db/studentPg');
    await pg.query(
      `INSERT INTO students (id, email, email_norm, password_hash, created_at, last_login_at)
       VALUES ($1,$2,$3,$4,$5,$6)
       ON CONFLICT (id) DO NOTHING`,
      [st.id, st.email, st.emailNorm, st.passwordHash, st.createdAt, st.lastLoginAt || st.createdAt]
    );
    imported.students += 1;
  }

  const pg = require('../../server/src/db/studentPg');
  for (const [studentId, profile] of Object.entries(profiles)) {
    await pg.query(
      `INSERT INTO student_profiles (student_id, profile, updated_at)
       VALUES ($1, $2::jsonb, $3)
       ON CONFLICT (student_id) DO UPDATE SET profile = EXCLUDED.profile, updated_at = EXCLUDED.updated_at`,
      [studentId, JSON.stringify(profile), profile.updatedAt || new Date().toISOString()]
    );
    imported.profiles += 1;
  }

  for (const item of items) {
    await pg.query(
      `INSERT INTO desk_items
        (id, student_id, kind, ref_id, title, board, status, exam_date, last_date, official_url, notes, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)
       ON CONFLICT (id) DO NOTHING`,
      [
        item.id,
        item.studentId,
        item.kind,
        item.refId,
        item.title,
        item.board || '',
        item.status,
        item.examDate,
        item.lastDate,
        item.officialUrl || '',
        item.notes || '',
        item.createdAt,
        item.updatedAt,
      ]
    );
    imported.items += 1;
  }

  for (const file of files) {
    await pg.query(
      `INSERT INTO desk_files (id, item_id, kind, stored_path, mime, bytes, original_name, uploaded_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8)
       ON CONFLICT (item_id, kind) DO NOTHING`,
      [
        file.id,
        file.itemId,
        file.kind,
        file.storedPath,
        file.mime,
        file.bytes,
        file.originalName,
        file.uploadedAt,
      ]
    );
    imported.files += 1;
  }

  for (const row of topics) {
    await pg.query(
      `INSERT INTO topic_progress (student_id, series_id, topic_id, done_at)
       VALUES ($1,$2,$3,$4)
       ON CONFLICT (student_id, series_id, topic_id) DO UPDATE SET done_at = EXCLUDED.done_at`,
      [row.studentId, row.seriesId, row.topicId, row.doneAt]
    );
    imported.topics += 1;
  }

  for (const row of attempts) {
    await pg.query(
      `INSERT INTO mock_attempts
        (id, student_id, series_id, item_id, started_at, submitted_at, score, total, answers)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9::jsonb)
       ON CONFLICT (id) DO NOTHING`,
      [
        row.id,
        row.studentId,
        row.seriesId,
        row.itemId || null,
        row.startedAt,
        row.submittedAt || null,
        row.score,
        row.total,
        JSON.stringify(row.answers || {}),
      ]
    );
    imported.attempts += 1;
  }

  console.log(`Imported from ${path.resolve(src)}`);
  console.log(imported);
  await pg.close();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
