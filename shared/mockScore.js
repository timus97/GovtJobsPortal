/**
 * Unofficial mock scoring. Never leak answers from publicBank.
 */

function publicBank(bank) {
  if (!bank || typeof bank !== 'object') return null;
  return {
    seriesId: bank.seriesId,
    unofficial: true,
    durationMin: Number(bank.durationMin) > 0 ? Number(bank.durationMin) : 20,
    questions: (Array.isArray(bank.questions) ? bank.questions : []).map((q) => ({
      id: q.id,
      stem: q.stem,
      choices: Array.isArray(q.choices) ? q.choices.slice() : [],
    })),
  };
}

function chosenIndex(answers, questionId) {
  if (!answers || typeof answers !== 'object') return null;
  if (!Object.prototype.hasOwnProperty.call(answers, questionId)) return null;
  const raw = answers[questionId];
  const n = typeof raw === 'number' ? raw : Number(raw);
  if (!Number.isInteger(n)) return null;
  return n;
}

function scoreAttempt(bank, answers) {
  const questions = bank && Array.isArray(bank.questions) ? bank.questions : [];
  const correctIds = [];
  const review = questions.map((q) => {
    const chosen = chosenIndex(answers, q.id);
    const ok = chosen !== null && chosen === q.answerIndex;
    if (ok) correctIds.push(q.id);
    return {
      id: q.id,
      stem: q.stem,
      choices: Array.isArray(q.choices) ? q.choices.slice() : [],
      chosen,
      answerIndex: q.answerIndex,
      explain: q.explain,
      ok,
    };
  });
  return {
    score: correctIds.length,
    total: questions.length,
    correctIds,
    review,
  };
}

module.exports = { publicBank, scoreAttempt };
