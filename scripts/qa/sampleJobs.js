const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..', '..');
const jobsPath = path.join(root, 'data', 'processed', 'jobs.json');

function main() {
  const jobs = JSON.parse(fs.readFileSync(jobsPath, 'utf8'));
  const open = jobs.filter((j) => j.status !== 'closed');
  const sample = open.sort(() => Math.random() - 0.5).slice(0, 10);
  console.log(`Sample of ${sample.length} open/closing jobs for human QA:\n`);
  for (const j of sample) {
    console.log(`- [${j.orgType}] ${j.title}`);
    console.log(`  Org: ${j.organization}`);
    console.log(`  Selection: ${j.selectionProcess} | Last: ${j.lastDate} | Status: ${j.status}`);
    console.log(`  Official: ${j.officialUrl}`);
    console.log('');
  }
}

main();
