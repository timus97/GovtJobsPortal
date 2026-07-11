/**
 * Copy jobs.json → jobs.prev.json after alerts have run.
 */
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..', '..');
const jobsPath = path.join(root, 'data', 'processed', 'jobs.json');
const prevPath = path.join(root, 'data', 'processed', 'jobs.prev.json');

if (!fs.existsSync(jobsPath)) {
  console.error('Missing data/processed/jobs.json — run process first');
  process.exit(1);
}

fs.copyFileSync(jobsPath, prevPath);
console.log(`Snapshot: jobs.json → jobs.prev.json (${fs.statSync(prevPath).size} bytes)`);
