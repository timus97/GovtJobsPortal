const fs = require('fs');
const path = require('path');
const express = require('express');
const cors = require('cors');
const jobsRouter = require('./routes/jobs');
const matchRouter = require('./routes/match');
const opsRouter = require('./routes/ops');
const { router: accountRouter, meRouter } = require('./routes/account');
const { router: coachingRouter, mePlanRouter } = require('./routes/coaching');
const { router: mocksPublicRouter, meMocksRouter } = require('./routes/mocks');
const operatorStore = require('./services/operatorStore');
const studentStore = require('./services/studentStore');
const collectQueue = require('./services/collectQueue');
const sqlite = require('./db/sqlite');
const { rebuildCache } = require('../../scripts/migrate/jsonToSqlite');
const logger = require('./services/logger');
const studentMail = require('./services/studentMail');
const { attachRequestContext, requestLogger } = require('./middleware/requestContext');

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
app.use(attachRequestContext);
app.use(requestLogger);

const jobsJsonPath = path.join(__dirname, '..', '..', 'data', 'processed', 'jobs.json');

app.get('/api/session', (req, res) => {
  res.json({
    student: req.student ? { id: req.student.uid, email: req.student.sub } : null,
    ops: req.ops ? { username: req.ops.sub, role: req.ops.role || 'operator' } : null,
  });
});

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
    studentStore: studentStore.BACKEND || studentStore.BACKEND_NAME,
    mail: studentMail.status(),
  });
});

app.use('/api/ops', opsRouter);
app.use('/api/account', accountRouter);
app.use('/api/me', meRouter);
app.use('/api/coaching', coachingRouter);
app.use('/api/coaching', mocksPublicRouter);
app.use('/api/me', mePlanRouter);
app.use('/api/me', meMocksRouter);
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
      logger.info('sqlite', 'Catalog cache rebuilt', {
        role: 'system',
        action: 'cache.rebuild',
        meta: report.upserted,
      });
    } else if (report.status === 'off') {
      logger.info('sqlite', 'Catalog cache off', { role: 'system', action: 'cache.off' });
    } else {
      logger.warn('sqlite', 'Catalog cache missing', {
        role: 'system',
        action: 'cache.missing',
        meta: { reason: report.reason || 'unavailable' },
      });
    }
    return report;
  } catch (err) {
    logger.warn('sqlite', `Catalog cache rebuild skipped: ${err.message}`, {
      role: 'system',
      action: 'cache.rebuild_failed',
    });
    sqlite.setStatus('missing');
    return { status: 'missing', reason: err.message };
  }
}

async function start() {
  rebuildSqliteCache();
  try {
    const ready = await studentStore.ready();
    logger.info('student.store', `Student store ready (${ready.backend})`, {
      role: 'system',
      action: 'store.ready',
      meta: { backend: ready.backend },
    });
  } catch (err) {
    logger.warn('student.store', `Student store init skipped: ${err.message}`, {
      role: 'system',
      action: 'store.init_failed',
    });
  }
  return app.listen(PORT, () => {
    try {
      const boot = operatorStore.bootstrapIfEmpty();
      if (boot.created) {
        logger.info('auth.ops', 'Bootstrapped first operator account: admin', {
          role: 'system',
          actor: 'admin',
          action: 'ops.bootstrap',
        });
      }
    } catch (err) {
      logger.warn('auth.ops', `Operator bootstrap skipped: ${err.message}`, {
        role: 'system',
        action: 'ops.bootstrap_failed',
      });
    }
    try {
      studentStore.warnIfUnwritable();
    } catch (err) {
      logger.warn('student.store', `Student store check skipped: ${err.message}`, {
        role: 'system',
        action: 'store.check_failed',
      });
    }
    try {
      collectQueue.resumePending();
    } catch (err) {
      logger.warn('collect', `Collect queue resume skipped: ${err.message}`, {
        role: 'system',
        action: 'collect.resume_failed',
      });
    }
    logger.info('http', `API listening on http://localhost:${PORT}`, {
      role: 'system',
      action: 'server.listen',
      meta: { port: PORT },
    });
  });
}

if (require.main === module) {
  start().catch((err) => {
    logger.error('http', err.message || 'Server failed to start', {
      role: 'system',
      action: 'server.crash',
    });
    process.exit(1);
  });
}

module.exports = { app, start, rebuildSqliteCache };
