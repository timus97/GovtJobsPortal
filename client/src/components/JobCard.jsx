import { Link } from 'react-router-dom'
import '../App.css'
import {
  ORG_TYPE_LABELS,
  SELECTION_LABELS,
  STATUS_LABELS,
  formatDate,
} from '../utils/labels'

export default function JobCard({ job }) {
  return (
    <article className="job-card">
      <div className="job-card-top">
        <div className="badge-row">
          <span className={`badge org-${job.orgType}`}>{ORG_TYPE_LABELS[job.orgType] || job.orgType}</span>
          <span className="badge badge-soft">{SELECTION_LABELS[job.selectionProcess] || job.selectionProcess}</span>
          {job.hasExam === true && <span className="badge badge-exam">Exam</span>}
          {job.status === 'closing_soon' && (
            <span className="badge badge-warn">{STATUS_LABELS.closing_soon}</span>
          )}
          {job.status === 'closed' && (
            <span className="badge badge-muted">{STATUS_LABELS.closed}</span>
          )}
        </div>
        <h3 className="job-title">
          <Link to={`/jobs/${job.id}`}>{job.title}</Link>
        </h3>
        <p className="job-org">{job.organization}</p>
      </div>
      <dl className="job-meta">
        <div>
          <dt>Location</dt>
          <dd>{job.location || '—'}</dd>
        </div>
        <div>
          <dt>Last date</dt>
          <dd>{formatDate(job.lastDate)}</dd>
        </div>
        <div>
          <dt>Sector</dt>
          <dd>{job.sector || '—'}</dd>
        </div>
        {job.vacancies != null && (
          <div>
            <dt>Vacancies</dt>
            <dd>{job.vacancies}</dd>
          </div>
        )}
      </dl>
      <div className="job-card-actions">
        <Link to={`/jobs/${job.id}`} className="btn btn-secondary">
          View details
        </Link>
        <a
          href={job.officialUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="btn btn-primary"
        >
          Official site
        </a>
      </div>
    </article>
  )
}
