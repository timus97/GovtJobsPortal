export const ORG_TYPE_LABELS = {
  central: 'Central Govt',
  psu: 'PSU',
  govt_company: 'Govt Company',
  autonomous: 'Autonomous',
}

export const SELECTION_LABELS = {
  walk_in: 'Walk-in Interview',
  interview_only: 'Interview Only',
  merit: 'Merit Based',
  contract_interview: 'Contract + Interview',
  direct_recruitment: 'Direct Recruitment',
  apprenticeship: 'Apprenticeship',
  cbt: 'Computer-based test',
  written_multi_stage: 'Written (multi-stage)',
  interview_after_exam: 'Exam + interview',
  physical: 'Physical / PET-PST',
}

export const HAS_EXAM_LABELS = {
  all: 'All jobs',
  yes: 'Exam-based',
  no: 'No written exam',
}

export const QUAL_LABELS = {
  below_10: 'Below 10th',
  '10th': '10th',
  '12th': '12th',
  iti: 'ITI',
  diploma: 'Diploma',
  graduate: 'Graduate',
  pg: 'Postgraduate',
  experience: 'Experience-based',
}

export const STATUS_LABELS = {
  open: 'Open',
  closing_soon: 'Closing Soon',
  closed: 'Closed',
}

export function formatDate(iso) {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleDateString('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    })
  } catch {
    return iso
  }
}

export function formatDateTime(iso) {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleString('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return iso
  }
}
