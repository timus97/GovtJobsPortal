/**
 * One-shot: disable _2/_3 PSU clones and hosts stolen by acronym collisions.
 */
const fs = require('fs');
const path = require('path');

const file = path.join(__dirname, '..', '..', 'data', 'sources', 'registry.json');
const data = JSON.parse(fs.readFileSync(file, 'utf8'));
let disabled = 0;

for (const source of data.sources || []) {
  if (/_\d+$/.test(source.sourceId) && source.enabled) {
    source.enabled = false;
    source.robotsNotes = `${source.robotsNotes || ''} Disabled clone (_2/_3); do not scrape the same host twice.`.trim();
    disabled += 1;
  }
}

data.updatedAt = new Date().toISOString().slice(0, 10);
fs.writeFileSync(file, `${JSON.stringify(data, null, 2)}\n`);
const still = (data.sources || []).filter((s) => /_\d+$/.test(s.sourceId) && s.enabled).map((s) => s.sourceId);
console.log(JSON.stringify({ disabled, stillClones: still }, null, 2));
