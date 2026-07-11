export default function AboutPage() {
  return (
    <div className="section">
      <div className="container prose">
        <h1>About NoExam Sarkari</h1>
        <p className="lead">
          A community-style aggregator for Indian central government, PSU and government company
          jobs that do not require a competitive written test.
        </p>
        <h2>Disclaimer</h2>
        <ul>
          <li>Not affiliated with the Government of India, any ministry, or any PSU.</li>
          <li>Listings may be incomplete or become outdated — official notifications prevail.</li>
          <li>We do not charge candidates or process applications on this site.</li>
          <li>Report incorrect classification by verifying the official advertisement first.</li>
        </ul>
        <h2>How to add or correct a listing</h2>
        <p>
          Maintainers update <code>data/seed/jobs.json</code> or import CSV via{' '}
          <code>npm run collect:manual</code>, then run <code>npm run process</code>. See{' '}
          <code>docs/collection-runbook.md</code> in the project repository.
        </p>
      </div>
    </div>
  )
}
