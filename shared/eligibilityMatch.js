/**
 * Rule-based ACTIVE eligibility matcher.
 * Unknown ≠ fail. Never invent age relaxations. No NLP of eligibility[].
 */

const facts = require('./eligibilityFacts');

/** Same close-date math as jobSchema.computeStatus. Null is handled by the Status rule, not here. */
function statusFromCloseDate(lastDate, today = new Date()) {
  if (!lastDate) return 'open';
  const end = new Date(lastDate);
  if (Number.isNaN(end.getTime())) return 'open';
  const t = new Date(today);
  t.setHours(0, 0, 0, 0);
  end.setHours(0, 0, 0, 0);
  if (end < t) return 'closed';
  const diffDays = (end - t) / (1000 * 60 * 60 * 24);
  if (diffDays <= 7) return 'closing_soon';
  return 'open';
}

const LOW_CONFIDENCE = 0.6;
const LAST_DATE_UNKNOWN_CHIP = 'Last date not listed — verify on official site';
const VERIFY_BADGE = 'Low confidence — verify on official site';

function reason(rule, outcome, detail) {
  return { rule, outcome, detail };
}

function profileHasDisability(profile) {
  return Boolean(profile && profile.pwbd && profile.pwbd.hasDisability);
}

function evaluateStatus(extracted) {
  const close = extracted.applicationClose;
  if (!close) {
    return reason('status', 'unknown', LAST_DATE_UNKNOWN_CHIP);
  }
  const status = statusFromCloseDate(close);
  if (status === 'closed') {
    return reason('status', 'fail', `Application closed on ${close}`);
  }
  if (status === 'closing_soon') {
    return reason('status', 'pass', `Last date ${close} (closing soon)`);
  }
  return reason('status', 'pass', `Last date ${close}`);
}

function evaluateAge(profile, extracted) {
  const bandMin = extracted.ageMin;
  const bandMax = extracted.ageMax;
  const asOn = extracted.ageAsOnDate;
  if (!asOn || bandMin == null || bandMax == null) {
    return reason('age', 'unknown', `Age band or as-on date not listed — verify on official site`);
  }

  const age = facts.ageOnDate(profile.dob, asOn);
  if (age == null) {
    return reason('age', 'unknown', `Age band or as-on date not listed — verify on official site`);
  }

  const category = facts.normalizeCategory(profile.reservationCategory);
  const printed = extracted.ageRelaxation;
  const extra = printed && category && printed[category] != null ? Number(printed[category]) : 0;
  const appliedRelaxation = Number.isFinite(extra) && extra > 0;
  const effectiveMax = bandMax + (appliedRelaxation ? extra : 0);

  const inBand = age >= bandMin && age <= effectiveMax;
  const relaxNote = appliedRelaxation
    ? ` after printed ${category} +${extra}`
    : category
      ? ` (${category}, no printed relaxation)`
      : '';

  if (inBand) {
    return reason(
      'age',
      'pass',
      `Age ${age} on ${asOn} is within ${bandMin}–${effectiveMax}${relaxNote}`
    );
  }
  return reason(
    'age',
    'fail',
    `Age ${age} on ${asOn} is outside ${bandMin}–${effectiveMax}${relaxNote}`
  );
}

function evaluateEducation(profile, extracted) {
  const required = extracted.minEducation;
  if (required == null) {
    return reason('education', 'unknown', `Education requirement not parsed — verify on official site`);
  }
  if (required === 'experience') {
    return reason(
      'education',
      'unknown',
      `Experience-based requirement is not compared to a degree — verify on official site`
    );
  }
  const outcome = facts.compareEducation(profile.highestEducation, required);
  const have = facts.normalizeQualification(profile.highestEducation) || profile.highestEducation;
  if (outcome === 'unknown') {
    return reason('education', 'unknown', `Education requirement not parsed — verify on official site`);
  }
  if (outcome === 'pass') {
    return reason('education', 'pass', `${have} meets ${required} requirement`);
  }
  return reason('education', 'fail', `${have} is below ${required} requirement`);
}

function evaluateDiscipline(profile, extracted) {
  const required = extracted.disciplineRequired;
  if (required == null || required === '' || required === 'any') {
    return reason('discipline', 'pass', 'No discipline requirement');
  }
  const known = facts.normalizeDiscipline(required);
  if (!known) {
    return reason('discipline', 'unknown', `Discipline requirement not parsed — verify on official site`);
  }
  const have = facts.normalizeDiscipline(profile.educationDiscipline);
  if (have === 'any' || have === known) {
    return reason('discipline', 'pass', `${have || 'any'} matches ${known}`);
  }
  if (!have) {
    return reason('discipline', 'fail', `Required ${known}; profile discipline missing`);
  }
  return reason('discipline', 'fail', `Required ${known}; profile has ${have}`);
}

function evaluateDomicile(profile, extracted) {
  if (!extracted.domicileRequired) {
    return reason('domicile', 'pass', 'No domicile restriction');
  }
  const required = (extracted.domicileStates || []).map((s) => String(s).toUpperCase());
  if (!required.length) {
    return reason(
      'domicile',
      'unknown',
      'Local-candidate requirement listed without states — verify on official site'
    );
  }
  const have = new Set(
    [...(profile.domicileStates || []), profile.birthState]
      .filter(Boolean)
      .map((s) => String(s).toUpperCase())
  );
  const overlap = required.filter((s) => have.has(s));
  if (overlap.length) {
    return reason('domicile', 'pass', `Domicile overlaps ${overlap.join(', ')}`);
  }
  return reason(
    'domicile',
    'fail',
    `Required domicile ${required.join(', ')}; profile has ${[...have].join(', ') || 'none'}`
  );
}

function evaluateGender(profile, extracted) {
  const required = extracted.genderRequired;
  if (!required) {
    return reason('gender', 'pass', 'No gender restriction');
  }
  const have = facts.normalizeGender(profile.gender);
  if (have === required) {
    return reason('gender', 'pass', `Matches ${required}-only requirement`);
  }
  if (!have) {
    return reason('gender', 'fail', `This post requires ${required}; profile gender is missing`);
  }
  return reason('gender', 'fail', `This post requires ${required}; profile gender is ${have}`);
}

function evaluatePwbd(profile, extracted) {
  if (!profileHasDisability(profile)) {
    return reason('pwbd', 'pass', 'No disability declared');
  }
  if (extracted.pwbdAllowed === true) {
    return reason('pwbd', 'pass', 'PwBD vacancies mentioned');
  }
  if (extracted.pwbdAllowed === false) {
    return reason('pwbd', 'fail', 'Notification does not allow PwBD');
  }
  return reason('pwbd', 'unknown', 'PwBD suitability not listed — verify on official site');
}

function closeSortKey(extracted) {
  return extracted.applicationClose || '9999-12-31';
}

function matchOne(profile, opportunity) {
  const extracted = facts.extractOpportunityFacts(opportunity);
  const reasons = [
    evaluateStatus(extracted),
    evaluateAge(profile, extracted),
    evaluateEducation(profile, extracted),
    evaluateDiscipline(profile, extracted),
    evaluateDomicile(profile, extracted),
    evaluateGender(profile, extracted),
    evaluatePwbd(profile, extracted),
  ];

  const applicableRules = reasons.length;
  const coveredRules = reasons.filter((r) => r.outcome !== 'unknown').length;
  const fails = reasons.filter((r) => r.outcome === 'fail').length;
  const confidence =
    applicableRules === 0 ? 0 : Math.round((coveredRules / applicableRules) * 10000) / 10000;
  const score = Math.round(100 * confidence * (fails === 0 ? 1 : 0) * 100) / 100;

  return {
    id: opportunity.id || extracted.id,
    title: opportunity.title || extracted.title,
    organization: opportunity.organization || extracted.organization,
    officialUrl: opportunity.officialUrl || extracted.officialUrl,
    lastDate: opportunity.lastDate ?? extracted.applicationClose ?? null,
    applicationClose: extracted.applicationClose,
    score,
    confidence,
    reasons,
    fails,
    lowConfidence: fails === 0 && confidence < LOW_CONFIDENCE,
    _closeSortKey: closeSortKey(extracted),
  };
}

function requireMatchableProfile(profile) {
  const v = facts.validateMatchProfile(profile);
  if (!v.ok) {
    const err = new Error(v.errors.join('; '));
    err.statusCode = 400;
    err.errors = v.errors;
    throw err;
  }
}

function rankMatches(rows) {
  return [...rows].sort((a, b) => {
    const closeCmp = a._closeSortKey.localeCompare(b._closeSortKey);
    if (closeCmp !== 0) return closeCmp;
    return b.score - a.score;
  });
}

function stripInternal(row) {
  const { _closeSortKey, fails, ...rest } = row;
  return rest;
}

/**
 * @param {object} profile
 * @param {object[]} opportunities
 * @returns {{ matches: object[], excluded: object[] }}
 */
function matchOpportunities(profile, opportunities) {
  requireMatchableProfile(profile);
  const list = Array.isArray(opportunities) ? opportunities : [];
  const scored = list.map((opp) => matchOne(profile, opp));
  const matches = rankMatches(scored.filter((r) => r.fails === 0)).map(stripInternal);
  const excluded = scored.filter((r) => r.fails > 0).map(stripInternal);
  return { matches, excluded };
}

const AGE_WHEN_NOTIFIED = 'Age will be computed when notification is out';

function matchOneSeries(profile, series) {
  const reasons = [];
  const minEducation = series.minEducation || series.min_education || null;
  if (!minEducation) {
    reasons.push(
      reason('education', 'unknown', 'Typical education floor not listed — verify when notification is out')
    );
  } else {
    const cmp = facts.compareEducation(profile.highestEducation, minEducation);
    if (cmp === 'pass') {
      reasons.push(reason('education', 'pass', `Education meets typical ${minEducation} floor`));
    } else if (cmp === 'fail') {
      reasons.push(reason('education', 'fail', `Typical floor is ${minEducation}`));
    } else {
      reasons.push(
        reason('education', 'unknown', 'Typical education floor not listed — verify when notification is out')
      );
    }
  }

  if (!series.ageAsOnDate || series.ageMin == null || series.ageMax == null) {
    reasons.push(reason('age', 'unknown', AGE_WHEN_NOTIFIED));
  } else {
    reasons.push(evaluateAge(profile, series));
  }

  const openIds = series.linkedOpportunityIds || [];
  if (series.applyNever) {
    reasons.push(reason('apply', 'unknown', 'Prepare-for only — not a vacancy'));
  } else if (openIds.length) {
    reasons.push(reason('apply', 'pass', 'A linked apply window is open'));
  } else {
    reasons.push(reason('apply', 'unknown', 'No open apply window yet — start preparing'));
  }

  const fails = reasons.filter((r) => r.outcome === 'fail').length;
  const covered = reasons.filter((r) => r.outcome !== 'unknown').length;
  const confidence = reasons.length ? Math.round((covered / reasons.length) * 10000) / 10000 : 0;
  return {
    id: series.id,
    title: series.name,
    board: series.board,
    officialUrl: series.officialUrl,
    cycle: series.cycle || null,
    applyNever: Boolean(series.applyNever),
    canApply: !series.applyNever && openIds.length > 0,
    linkedOpportunityIds: openIds,
    score: Math.round(100 * confidence * (fails === 0 ? 1 : 0) * 100) / 100,
    confidence,
    reasons,
    fails,
  };
}

function matchExamSeries(profile, seriesList) {
  requireMatchableProfile(profile);
  const list = Array.isArray(seriesList) ? seriesList : [];
  const scored = list.map((s) => matchOneSeries(profile, s));
  const matches = scored.filter((r) => r.fails === 0).sort((a, b) => b.score - a.score);
  const excluded = scored.filter((r) => r.fails > 0);
  return { matches, excluded };
}

module.exports = {
  matchOpportunities,
  matchOne,
  matchExamSeries,
  matchOneSeries,
  requireMatchableProfile,
  LOW_CONFIDENCE,
  LAST_DATE_UNKNOWN_CHIP,
  VERIFY_BADGE,
  AGE_WHEN_NOTIFIED,
};

// Named bindings for bundlers that parse CJS
exports.matchOpportunities = matchOpportunities;
exports.matchOne = matchOne;
exports.requireMatchableProfile = requireMatchableProfile;
exports.LOW_CONFIDENCE = LOW_CONFIDENCE;
exports.LAST_DATE_UNKNOWN_CHIP = LAST_DATE_UNKNOWN_CHIP;
exports.VERIFY_BADGE = VERIFY_BADGE;
exports.matchExamSeries = matchExamSeries;
exports.matchOneSeries = matchOneSeries;
exports.AGE_WHEN_NOTIFIED = AGE_WHEN_NOTIFIED;
