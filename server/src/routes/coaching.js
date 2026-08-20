const express = require('express');
const studentAuth = require('../services/studentAuth');
const planStore = require('../services/planStore');

const router = express.Router();
const mePlanRouter = express.Router();

function requireFeature(_req, res, next) {
  if (!studentAuth.featureStudentOn()) {
    return res.status(404).json({ error: 'Student accounts are not enabled' });
  }
  next();
}

router.use(requireFeature);

router.get('/syllabus/:seriesId', (req, res) => {
  const pack = planStore.getSyllabusPack(req.params.seriesId);
  if (!pack) return res.status(404).json({ error: 'Syllabus not found' });
  res.json(pack);
});

mePlanRouter.use(requireFeature);

mePlanRouter.get('/plan/:seriesId', studentAuth.requireStudent, async (req, res) => {
  try {
    const result = await planStore.getPlanForStudent(req.student.uid, req.params.seriesId, req.query.itemId);
    if (!result) return res.status(404).json({ error: 'Syllabus not found' });
    res.json(result);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Failed to load plan' });
  }
});

mePlanRouter.put('/plan/:seriesId/topics/:topicId', studentAuth.requireStudent, async (req, res) => {
  const done = req.body && req.body.done;
  if (typeof done !== 'boolean') {
    return res.status(400).json({ error: 'done must be true or false' });
  }
  try {
    const result = await planStore.setTopicDone(req.student.uid, req.params.seriesId, req.params.topicId, done);
    res.json(result);
  } catch (err) {
    if (err.code === 'NOT_FOUND') return res.status(404).json({ error: err.message });
    console.error(err);
    res.status(500).json({ error: 'Failed to update topic' });
  }
});

module.exports = { router, mePlanRouter };
