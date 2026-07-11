const path = require('path');
const express = require('express');
const cors = require('cors');
const jobsRouter = require('./routes/jobs');

const app = express();
const PORT = process.env.PORT || 4000;

app.use(cors());
app.use(express.json());

app.get('/api/health', (_req, res) => {
  res.json({ ok: true, service: 'govt-jobs-portal', time: new Date().toISOString() });
});

app.use('/api', jobsRouter);

// Serve client build in production if present
const clientDist = path.join(__dirname, '..', '..', 'client', 'dist');
app.use(express.static(clientDist));
app.get('*', (req, res, next) => {
  if (req.path.startsWith('/api')) return next();
  const index = path.join(clientDist, 'index.html');
  res.sendFile(index, (err) => {
    if (err) next();
  });
});

app.listen(PORT, () => {
  console.log(`Govt Jobs Portal API listening on http://localhost:${PORT}`);
  console.log(`  GET /api/jobs  /api/stats  /api/health`);
});
