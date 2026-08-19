/**
 * PR05 eligibility match checks. Run: node tests/pr05-match.js
 */
const assert = require('assert');
const path = require('path');
const {
  ageOnDate,
  normalizeQualification,
  compareEducation,
  educationRank,
  extractOpportunityFacts,
  validateMatchProfile,
} = require(path.join(__dirname, '..', 'shared', 'eligibilityFacts'));
const {
  matchOpportunities,
  LAST_DATE_UNKNOWN_CHIP,
  LOW_CONFIDENCE,
} = require(path.join(__dirname, '..', 'shared', 'eligibilityMatch'));
const golden = require('./fixtures/golden-opportunities.json');

function fail(message, err) {
  console.error(`FAIL: ${message}`);
  if (err) console.error(err.message || err);
  process.exit(1);
}

function byId(id) {
  const row = golden.find((o) => o.id === id);
  assert.ok(row, `missing golden fixture ${id}`);
  return row;
}

function reason(row, rule) {
  return (row.reasons || []).find((r) => r.rule === rule);
}

const baseProfile = {
  dob: '1998-06-15',
  highestEducation: 'graduate',
  educationDiscipline: 'any',
  birthState: 'MH',
  domicileStates: ['MH'],
  gender: 'male',
  pwbd: { hasDisability: false, category: 'none' },
  reservationCategory: 'UR',
};

try {
  assert.strictEqual(normalizeQualification('postgraduate'), 'pg');
  assert.strictEqual(educationRank('pg'), educationRank('postgraduate'));
  assert.strictEqual(compareEducation('graduate', '12th'), 'pass');
  assert.strictEqual(compareEducation('12th', 'graduate'), 'fail');
  assert.strictEqual(compareEducation('graduate', 'experience'), 'unknown');
  assert.strictEqual(compareEducation('experience', 'graduate'), 'unknown');

  // Age uses notification as-on date, not today.
  // dob 1995-08-02: age 30 on 2026-08-01; age 31 on 2026-08-19 (today in this session).
  assert.strictEqual(ageOnDate('1995-08-02', '2026-08-01'), 30);
  assert.strictEqual(ageOnDate('1995-08-02', '2026-08-19'), 31);

  const asOnProfile = {
    ...baseProfile,
    dob: '1995-08-02',
    reservationCategory: 'UR',
  };
  const ageBand = matchOpportunities(asOnProfile, [byId('golden-age-band')]);
  const ageMatch = ageBand.matches.find((m) => m.id === 'golden-age-band');
  assert.ok(ageMatch, 'age 30 on as-on date should pass 18–30');
  const ageReason = reason(ageMatch, 'age');
  assert.strictEqual(ageReason.outcome, 'pass');
  assert.match(ageReason.detail, /Age 30 on 2026-08-01/);
  assert.doesNotMatch(ageReason.detail, /today/i);

  // Printed OBC +3 applied; 32 is within 21–33.
  const obcPrinted = matchOpportunities(
    { ...baseProfile, dob: '1994-01-01', reservationCategory: 'OBC' },
    [byId('golden-age-band')]
  );
  const obcRow = obcPrinted.matches.find((m) => m.id === 'golden-age-band');
  assert.ok(obcRow, 'printed OBC relaxation must be applied');
  assert.match(reason(obcRow, 'age').detail, /printed OBC \+3/);

  // Same age against a notification with no printed table → fail (never invent).
  const invented = matchOpportunities(
    { ...baseProfile, dob: '1994-01-01', reservationCategory: 'OBC' },
    [byId('golden-no-invented-relaxation')]
  );
  assert.ok(
    invented.excluded.some((m) => m.id === 'golden-no-invented-relaxation'),
    'must not invent OBC +3 when the table is missing'
  );
  const inventedAge = reason(
    invented.excluded.find((m) => m.id === 'golden-no-invented-relaxation'),
    'age'
  );
  assert.strictEqual(inventedAge.outcome, 'fail');
  assert.doesNotMatch(inventedAge.detail, /\+3/);

  // Missing lastDate: include, Status=unknown, exact chip, do not say "open".
  const missingDate = matchOpportunities(baseProfile, [byId('golden-missing-lastdate')]);
  const missingRow = missingDate.matches.find((m) => m.id === 'golden-missing-lastdate');
  assert.ok(missingRow, 'null lastDate must stay in ACTIVE matches');
  const statusUnknown = reason(missingRow, 'status');
  assert.strictEqual(statusUnknown.outcome, 'unknown');
  assert.strictEqual(statusUnknown.detail, LAST_DATE_UNKNOWN_CHIP);
  assert.doesNotMatch(JSON.stringify(missingRow.reasons), /\bopen\b/i);

  // PwBD allowed null + disability → unknown, not fail.
  const pwbdProfile = {
    ...baseProfile,
    highestEducation: '12th',
    pwbd: { hasDisability: true, category: 'OH' },
  };
  const pwbdRes = matchOpportunities(pwbdProfile, [byId('golden-pwbd-unknown')]);
  const pwbdRow = pwbdRes.matches.find((m) => m.id === 'golden-pwbd-unknown');
  assert.ok(pwbdRow, 'PwBD unknown must not exclude');
  assert.strictEqual(reason(pwbdRow, 'pwbd').outcome, 'unknown');
  assert.ok(!pwbdRes.excluded.some((m) => m.id === 'golden-pwbd-unknown'));

  // Education fail → excluded.
  const eduFail = matchOpportunities(
    { ...baseProfile, highestEducation: '12th' },
    [byId('golden-education-fail')]
  );
  const eduRow = eduFail.excluded.find((m) => m.id === 'golden-education-fail');
  assert.ok(eduRow, '12th vs graduate must be excluded');
  assert.strictEqual(reason(eduRow, 'education').outcome, 'fail');
  assert.strictEqual(eduRow.score, 0);

  // Closed window excluded.
  const closed = matchOpportunities(baseProfile, [byId('golden-closed')]);
  assert.ok(closed.excluded.some((m) => m.id === 'golden-closed'));
  assert.ok(!closed.matches.some((m) => m.id === 'golden-closed'));
  assert.strictEqual(reason(closed.excluded.find((m) => m.id === 'golden-closed'), 'status').outcome, 'fail');

  // Missing reservationCategory / dob / education → 400-equivalent.
  const missingCat = validateMatchProfile({ ...baseProfile, reservationCategory: '' });
  assert.strictEqual(missingCat.ok, false);
  assert.ok(missingCat.errors.some((e) => /reservationCategory/i.test(e)));

  let threw = false;
  try {
    matchOpportunities({ ...baseProfile, reservationCategory: null }, golden);
  } catch (err) {
    threw = true;
    assert.strictEqual(err.statusCode, 400);
  }
  assert.ok(threw, 'matcher must reject missing reservationCategory');

  threw = false;
  try {
    matchOpportunities({ ...baseProfile, dob: '' }, golden);
  } catch (err) {
    threw = true;
    assert.strictEqual(err.statusCode, 400);
  }
  assert.ok(threw, 'matcher must reject missing dob');

  threw = false;
  try {
    matchOpportunities({ ...baseProfile, highestEducation: '' }, golden);
  } catch (err) {
    threw = true;
    assert.strictEqual(err.statusCode, 400);
  }
  assert.ok(threw, 'matcher must reject missing highestEducation');

  // Do not NLP eligibility[] — free-text age line must not become a band.
  const textOnly = {
    id: 'text-only',
    title: 'Unparsed notice',
    officialUrl: 'https://upsc.gov.in/',
    lastDate: '2027-12-01',
    eligibility: ['Age 21-30 years as on 01.08.2026', 'Must be graduate'],
  };
  const extracted = extractOpportunityFacts(textOnly);
  assert.strictEqual(extracted.ageMin, null);
  assert.strictEqual(extracted.ageMax, null);
  assert.strictEqual(extracted.minEducation, null);
  const textMatch = matchOpportunities(baseProfile, [textOnly]);
  assert.strictEqual(reason(textMatch.matches[0], 'age').outcome, 'unknown');
  assert.strictEqual(reason(textMatch.matches[0], 'education').outcome, 'unknown');

  // Scoring: unknown does not count as covered; fails zero the score.
  const missingDateRow = missingRow;
  const unknownCount = missingDateRow.reasons.filter((r) => r.outcome === 'unknown').length;
  const covered = missingDateRow.reasons.filter((r) => r.outcome !== 'unknown').length;
  assert.strictEqual(missingDateRow.reasons.length, 7);
  assert.ok(unknownCount >= 1);
  assert.strictEqual(
    missingDateRow.confidence,
    Math.round((covered / 7) * 10000) / 10000
  );
  assert.strictEqual(missingDateRow.score, Math.round(100 * missingDateRow.confidence * 100) / 100);
  assert.ok(eduRow.score === 0);

  // Rank: sooner lastDate first; missing dates last.
  const ranked = matchOpportunities(baseProfile, [
    byId('golden-missing-lastdate'),
    byId('golden-age-band'),
    byId('golden-pwbd-unknown'),
  ]);
  const ids = ranked.matches.map((m) => m.id);
  const je = ids.indexOf('golden-age-band');
  const clerk = ids.indexOf('golden-pwbd-unknown');
  const becil = ids.indexOf('golden-missing-lastdate');
  assert.ok(je >= 0 && clerk >= 0 && becil >= 0);
  assert.ok(je < clerk, 'sooner last date ranks first');
  assert.ok(clerk < becil, 'missing last date ranks last');

  // Low-confidence badge threshold exists for sparse facts.
  const sparse = matchOpportunities(baseProfile, [
    { id: 'sparse', officialUrl: 'https://ncs.gov.in/', lastDate: null, qualification: null },
  ]);
  assert.ok(sparse.matches[0]);
  assert.ok(sparse.matches[0].confidence < LOW_CONFIDENCE);
  assert.strictEqual(sparse.matches[0].lowConfidence, true);

  // 10k synthetic smoke (single-run stand-in for p95 < 200 ms).
  const synth = [];
  for (let i = 0; i < 10000; i += 1) {
    synth.push({
      id: `synth-${i}`,
      title: `Synthetic ${i}`,
      organization: 'Board',
      officialUrl: 'https://ssc.gov.in/',
      lastDate: i % 3 === 0 ? null : '2027-12-31',
      qualification: i % 2 === 0 ? 'graduate' : null,
      ageMin: 18,
      ageMax: 32,
      ageAsOnDate: '2026-08-01',
    });
  }
  const t0 = process.hrtime.bigint();
  const synthOut = matchOpportunities(baseProfile, synth);
  const ms = Number(process.hrtime.bigint() - t0) / 1e6;
  assert.ok(synthOut.matches.length + synthOut.excluded.length === 10000);
  assert.ok(ms < 200, `10k match took ${ms.toFixed(1)}ms (need < 200)`);

  console.log(`pr05-match: all passed (${ms.toFixed(1)}ms / 10k)`);
  process.exit(0);
} catch (err) {
  fail(err.message, err);
}
