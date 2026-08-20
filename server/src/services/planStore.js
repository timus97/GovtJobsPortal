const fs = require('fs');
const path = require('path');
const studentStore = require('./studentStore');
const jobStore = require('./jobStore');
const desk = require('../../../shared/deskGuidance');
const { buildPlan } = require('../../../shared/studyPlan');

const SERIES_ID_RE = /^[a-z0-9][a-z0-9-]*$/i;

function fail(code, message) {
  const err = new Error(message);
  err.code = code;
  throw err;
}

function syllabusDir() {
  return path.resolve(__dirname, '..', '..', '..', 'data', 'coaching', 'syllabus');
}

function syllabusPath(seriesId) {
  const id = String(seriesId || '').trim();
  if (!SERIES_ID_RE.test(id)) return null;
  const dir = syllabusDir();
  const file = path.resolve(dir, `${id}.json`);
  if (path.dirname(file) !== dir) return null;
  return file;
}

function normalizeTopic(topic) {
  if (!topic || topic.id == null || topic.title == null) return null;
  const id = String(topic.id).trim();
  const title = String(topic.title).trim();
  if (!id || !title) return null;
  const w = Number(topic.weight);
  return {
    id,
    title,
    weight: Number.isFinite(w) && w > 0 ? w : 1,
  };
}

function getSyllabusPack(seriesId) {
  const file = syllabusPath(seriesId);
  if (!file || !fs.existsSync(file)) return null;
  try {
    const raw = JSON.parse(fs.readFileSync(file, 'utf8'));
    if (!raw || raw.unofficial !== true || !Array.isArray(raw.topics)) return null;
    const topics = raw.topics.map(normalizeTopic).filter(Boolean);
    return {
      seriesId: String(seriesId).trim(),
      unofficial: true,
      topics,
    };
  } catch {
    return null;
  }
}

function resolveExamDate(studentId, seriesId, itemId) {
  let item = null;
  if (itemId) {
    const found = studentStore.getItem(studentId, String(itemId));
    if (found && found.kind === 'series' && found.refId === seriesId) {
      item = found;
    }
  }
  if (item && item.examDate) {
    return { examDate: item.examDate, item };
  }
  const series = jobStore.getExamSeriesById(seriesId);
  const examDate = series ? desk.formatIsoDate(series.expectedExam) : null;
  return { examDate, item };
}

function progressMap(studentId, seriesId) {
  const data = studentStore.load();
  const out = {};
  for (const row of data.topicProgress || []) {
    if (row.studentId === studentId && row.seriesId === seriesId && row.topicId && row.doneAt) {
      out[row.topicId] = row.doneAt;
    }
  }
  return out;
}

function getPlanForStudent(studentId, seriesId, itemId) {
  const pack = getSyllabusPack(seriesId);
  if (!pack) return null;
  const { examDate, item } = resolveExamDate(studentId, seriesId, itemId);
  return {
    unofficial: true,
    seriesId: pack.seriesId,
    plan: buildPlan(pack.topics, examDate),
    progress: progressMap(studentId, pack.seriesId),
    item,
  };
}

function setTopicDone(studentId, seriesId, topicId, done) {
  const pack = getSyllabusPack(seriesId);
  if (!pack) fail('NOT_FOUND', 'Syllabus not found');
  const id = String(topicId || '').trim();
  if (!pack.topics.some((t) => t.id === id)) fail('NOT_FOUND', 'Topic not found');
  const data = studentStore.load();
  const rows = Array.isArray(data.topicProgress) ? data.topicProgress : [];
  data.topicProgress = rows.filter(
    (r) => !(r.studentId === studentId && r.seriesId === pack.seriesId && r.topicId === id)
  );
  if (done) {
    data.topicProgress.push({
      studentId,
      seriesId: pack.seriesId,
      topicId: id,
      doneAt: new Date().toISOString(),
    });
  }
  studentStore.save(data);
  return getPlanForStudent(studentId, pack.seriesId);
}

module.exports = {
  getSyllabusPack,
  getPlanForStudent,
  setTopicDone,
};
