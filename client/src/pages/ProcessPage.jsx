export default function ProcessPage() {
  return (
    <div className="section">
      <div className="container prose">
        <h1>How recruitment is classified</h1>
        <p className="lead">
          Not every sarkari job needs a long competitive exam. Many central departments, PSUs and
          government companies fill roles through interviews, walk-ins, merit lists, or contracts.
          We also list exam-based notifications (CBT, SSC, UPSC, IBPS, and similar) and let you
          filter them on the jobs page.
        </p>

        <h2>Types we catalogue</h2>
        <ul>
          <li>
            <strong>Walk-in interview</strong> — appear at a venue with documents on a fixed date.
          </li>
          <li>
            <strong>Interview only</strong> — apply online, shortlist on CV/eligibility, then
            interview.
          </li>
          <li>
            <strong>Merit based</strong> — ranking on academic marks or experience, no written test.
          </li>
          <li>
            <strong>Contract / consultant</strong> — project tenure roles, usually interview-based.
          </li>
          <li>
            <strong>Apprenticeship</strong> — under the Apprentices Act; training stipend, not a
            regular permanent post (labelled clearly).
          </li>
        </ul>

        <h2>How this portal processes listings</h2>
        <ol className="steps">
          <li>Collect from registered sources (official careers pages, NCS, manual curator seed).</li>
          <li>Normalize organisation names, dates and fields into a common schema.</li>
          <li>Classify exam vs no-exam; keep both. Filter on the jobs page.</li>
          <li>Dedupe, mark open / closing soon / closed from last date.</li>
          <li>Publish to the website API — every card links to the official URL.</li>
        </ol>

        <h2>Your checklist before applying</h2>
        <ul className="check-list">
          <li>Open the official notification (PDF or careers page).</li>
          <li>Confirm whether the post is exam-based or interview/merit on the official notice.</li>
          <li>Check last date, fees, and eligibility carefully.</li>
          <li>Never share OTPs or pay unlisted agents — use only official channels.</li>
        </ul>
      </div>
    </div>
  )
}
