const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..', '..');
const { isValidJob } = require(path.join(root, 'shared', 'jobSchema'));

const jobsPath = path.join(root, 'data', 'processed', 'jobs.json');

function main() {
  if (!fs.existsSync(jobsPath)) {
    console.error('Missing data/processed/jobs.json — run npm run process first');
    process.exit(1);
  }
  const jobs = JSON.parse(fs.readFileSync(jobsPath, 'utf8'));
  let failed = 0;
  for (const job of jobs) {
    const errors = isValidJob(job);
    if (errors.length) {
      failed += 1;
      console.error(`FAIL ${job.id || job.title}: ${errors.join(', ')}`);
    }
  }
  if (failed) {
    console.error(`Schema check failed: ${failed} issue(s) in ${jobs.length} jobs`);
    process.exit(1);
  }
  console.log(`Schema OK: ${jobs.length} published jobs`);
}

main();
