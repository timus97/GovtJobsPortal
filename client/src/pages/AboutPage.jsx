export default function AboutPage() {
  return (
    <div className="section">
      <div className="container prose">
        <h1>About NoExam Sarkari</h1>
        <p className="lead">
          A community-style aggregator for Indian central government, PSU and government company
          jobs that do not require a competitive written test.
        </p>
        <h2>Where is the data stored?</h2>
        <p>
          There is <strong>no SQL database</strong>. Job listings and scrape metadata are stored as
          JSON files in the project (mainly <code>data/processed/jobs.json</code>). The public site
          serves a snapshot of those files. See the full guide:{' '}
          <a
            href="https://github.com/timus97/GovtJobsPortal/blob/main/docs/DATA_AND_STATUS.md"
            target="_blank"
            rel="noopener noreferrer"
          >
            Data storage, scrape status &amp; running jobs
          </a>
          .
        </p>
        <h2>Source badges (“Not scraped…”)</h2>
        <p>
          The Sources page lists every organisation we know about. A badge of{' '}
          <strong>Not in last scrape report</strong> means that organisation was not included in the
          last recorded collect run (or the report file is outdated)—it does <em>not</em> mean a
          scraper is running right now. Scrapes only run when the daily pipeline or a maintainer
          starts them.
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
          <code>docs/collection-runbook.md</code> and <code>docs/DATA_AND_STATUS.md</code> in the
          project repository.
        </p>
      </div>
    </div>
  )
}
