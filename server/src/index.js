const fs = require('fs');
const path = require('path');
const express = require('express');
const cors = require('cors');
const jobsRouter = require('./routes/jobs');
const matchRouter = require('./routes/match');
const opsRouter = require('./routes/ops');
const operatorStore = require('./services/operatorStore');
const sqlite = require('./db/sqlite');
const { rebuildCache } = require('../../scripts/migrate/jsonToSqlite');

const app = express();
const PORT = process.env.PORT || 4000;

app.set('trust proxy', 1);
app.use(
  cors({
    origin: true,
    credentials: true,
  })
);
app.use(express.json());

const jobsJsonPath = path.join(__dirname, '..', '..', 'data', 'processed', 'jobs.json');

app.get('/api/health', (_req, res) => {
  let jsonReadable = false;
  try {
    jsonReadable = fs.existsSync(jobsJsonPath);
  } catch {
    jsonReadable = false;
  }
  res.json({
    ok: jsonReadable,
    service: 'govt-jobs-portal',
    time: new Date().toISOString(),
    sqliteCache: sqlite.getStatus(),
  });
});

app.use('/api/ops', opsRouter);
app.use('/api', jobsRouter);
app.use('/api', matchRouter);

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

function rebuildSqliteCache() {
  try {
    const report = rebuildCache();
    if (report.status === 'ok') {
      console.log(
        `SQLite cache ok: ${report.upserted.opportunities} opportunities, ${report.upserted.sources} sources`
      );
    } else if (report.status === 'off') {
      console.log('SQLite cache off');
    } else {
      console.warn(`SQLite cache missing: ${report.reason || 'unavailable'}`);
    }
    return report;
  } catch (err) {
    console.warn(`SQLite cache rebuild skipped: ${err.message}`);
    sqlite.setStatus('missing');
    return { status: 'missing', reason: err.message };
  }
}

function start() {
  rebuildSqliteCache();
  return app.listen(PORT, () => {
    try {
      const boot = operatorStore.bootstrapIfEmpty();
      if (boot.created) {
        console.log('Bootstrapped first operator account: admin (rotate/remove OPERATOR_PASSWORD)');
      }
    } catch (err) {
      console.warn(`Operator bootstrap skipped: ${err.message}`);
    }
    console.log(`Govt Jobs Portal API listening on http://localhost:${PORT}`);
    console.log(`  GET /api/jobs  POST /api/match  /api/stats  /api/health  /api/ops/me`);
  });
}

if (require.main === module) {
  start();
}

module.exports = { app, start, rebuildSqliteCache };
