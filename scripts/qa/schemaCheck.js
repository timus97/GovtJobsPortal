const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..', '..');
const { isValidJob } = require(path.join(root, 'shared', 'jobSchema'));
const { isValidExamSeries } = require(path.join(root, 'shared', 'examSeriesSchema'));

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
  const seriesPath = path.join(root, 'data', 'processed', 'exam_series.json');
  let seriesCount = 0;
  if (fs.existsSync(seriesPath)) {
    const series = JSON.parse(fs.readFileSync(seriesPath, 'utf8'));
    seriesCount = series.length;
    for (const row of series) {
      const errors = isValidExamSeries(row);
      if (errors.length) {
        failed += 1;
        console.error(`FAIL series ${row.id || row.name}: ${errors.join(', ')}`);
      }
    }
  }
  if (failed) {
    console.error(`Schema check failed: ${failed} issue(s)`);
    process.exit(1);
  }
  console.log(`Schema OK: ${jobs.length} published jobs, ${seriesCount} exam series`);
}

main();
