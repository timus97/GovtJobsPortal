/**
 * Unofficial even-split study plan. Never invents topics or exam dates.
 */

const desk = require('./deskGuidance');

const ADD_DATE_NOTE = 'Add exam date to split the plan.';

function topicWeight(topic) {
  const w = Number(topic && topic.weight);
  return Number.isFinite(w) && w > 0 ? w : 1;
}

function addUtcDays(isoOrDate, n) {
  const d = isoOrDate instanceof Date ? new Date(isoOrDate.getTime()) : desk.parseIsoDate(isoOrDate);
  if (!d) return null;
  d.setUTCDate(d.getUTCDate() + n);
  return desk.formatIsoDate(d);
}

function splitDaysByWeight(topics, totalDays) {
  const weights = topics.map(topicWeight);
  const totalWeight = weights.reduce((sum, w) => sum + w, 0) || topics.length || 1;
  const raw = weights.map((w) => (totalDays * w) / totalWeight);
  const alloc = raw.map((r) => Math.floor(r));
  let leftover = totalDays - alloc.reduce((sum, n) => sum + n, 0);
  const order = raw
    .map((r, i) => ({ i, frac: r - Math.floor(r) }))
    .sort((a, b) => b.frac - a.frac || a.i - b.i);
  for (let k = 0; leftover > 0 && k < order.length; k += 1) {
    alloc[order[k].i] += 1;
    leftover -= 1;
  }
  return alloc;
}

function nullRangeTopics(topics) {
  return topics.map((topic) => ({
    ...topic,
    weight: topicWeight(topic),
    startDate: null,
    endDate: null,
    days: null,
  }));
}

/**
 * Split remaining UTC days (today..exam inclusive) across topics by weight.
 * daysLeft <= 0 or missing date → null ranges + add-exam-date note.
 * Fewer days than topics → later topics share the last day.
 */
function buildPlan(topics, examDate, today) {
  const list = Array.isArray(topics) ? topics : [];
  const now = today instanceof Date ? today : new Date();
  const left = desk.daysLeft(examDate, now);

  if (left == null || left <= 0) {
    return {
      unofficial: true,
      days: left == null ? null : left,
      note: ADD_DATE_NOTE,
      topics: nullRangeTopics(list),
    };
  }

  const totalDays = left + 1;
  const todayIso = desk.formatIsoDate(now);
  const examIso = desk.formatIsoDate(examDate);
  const alloc = splitDaysByWeight(list, totalDays);
  let offset = 0;

  const planned = list.map((topic, i) => {
    const n = alloc[i] || 0;
    if (n <= 0) {
      return {
        ...topic,
        weight: topicWeight(topic),
        startDate: examIso,
        endDate: examIso,
        days: 1,
      };
    }
    const startDate = addUtcDays(todayIso, offset);
    const endDate = addUtcDays(todayIso, offset + n - 1);
    offset += n;
    return {
      ...topic,
      weight: topicWeight(topic),
      startDate,
      endDate,
      days: n,
    };
  });

  return {
    unofficial: true,
    days: totalDays,
    topics: planned,
  };
}

module.exports = {
  ADD_DATE_NOTE,
  buildPlan,
  addUtcDays,
};

exports.ADD_DATE_NOTE = ADD_DATE_NOTE;
exports.buildPlan = buildPlan;
exports.addUtcDays = addUtcDays;
