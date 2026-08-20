process.env.STUDENT_STORE = process.env.STUDENT_STORE || 'postgres';
if (!process.env.STUDENT_DATABASE_URL && !process.env.DATABASE_URL) {
  process.env.STUDENT_DATABASE_URL = 'postgres://govtjobs:govtjobs@127.0.0.1:5432/govtjobs_students';
}
const { start } = require('../../server/src/index');
start().catch((err) => {
  console.error(err);
  process.exit(1);
});
