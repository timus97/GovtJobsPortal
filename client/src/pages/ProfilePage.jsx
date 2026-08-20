import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import '../App.css'
import { isProfileComplete, loadProfile, saveProfile } from '../lib/profile'

const EDUCATION_OPTIONS = [
  { value: 'below_10', label: 'Below 10th' },
  { value: '10th', label: '10th' },
  { value: '12th', label: '12th' },
  { value: 'iti', label: 'ITI' },
  { value: 'diploma', label: 'Diploma' },
  { value: 'graduate', label: 'Graduate' },
  { value: 'pg', label: 'Postgraduate' },
  { value: 'phd', label: 'PhD' },
  { value: 'experience', label: 'Experience only (not a degree rung)' },
]

const DISCIPLINE_OPTIONS = [
  { value: '', label: 'Not specified' },
  { value: 'any', label: 'Any' },
  { value: 'engineering', label: 'Engineering' },
  { value: 'commerce', label: 'Commerce' },
  { value: 'arts', label: 'Arts' },
  { value: 'science', label: 'Science' },
  { value: 'law', label: 'Law' },
  { value: 'medical', label: 'Medical' },
]

const CATEGORY_OPTIONS = [
  { value: 'UR', label: 'UR — Unreserved' },
  { value: 'EWS', label: 'EWS' },
  { value: 'OBC', label: 'OBC' },
  { value: 'SC', label: 'SC' },
  { value: 'ST', label: 'ST' },
]

const STATES = [
  { code: 'AN', name: 'Andaman and Nicobar Islands' },
  { code: 'AP', name: 'Andhra Pradesh' },
  { code: 'AR', name: 'Arunachal Pradesh' },
  { code: 'AS', name: 'Assam' },
  { code: 'BR', name: 'Bihar' },
  { code: 'CH', name: 'Chandigarh' },
  { code: 'CT', name: 'Chhattisgarh' },
  { code: 'DH', name: 'Dadra and Nagar Haveli and Daman and Diu' },
  { code: 'DL', name: 'Delhi' },
  { code: 'GA', name: 'Goa' },
  { code: 'GJ', name: 'Gujarat' },
  { code: 'HR', name: 'Haryana' },
  { code: 'HP', name: 'Himachal Pradesh' },
  { code: 'JK', name: 'Jammu and Kashmir' },
  { code: 'JH', name: 'Jharkhand' },
  { code: 'KA', name: 'Karnataka' },
  { code: 'KL', name: 'Kerala' },
  { code: 'LA', name: 'Ladakh' },
  { code: 'LD', name: 'Lakshadweep' },
  { code: 'MP', name: 'Madhya Pradesh' },
  { code: 'MH', name: 'Maharashtra' },
  { code: 'MN', name: 'Manipur' },
  { code: 'ML', name: 'Meghalaya' },
  { code: 'MZ', name: 'Mizoram' },
  { code: 'NL', name: 'Nagaland' },
  { code: 'OR', name: 'Odisha' },
  { code: 'PY', name: 'Puducherry' },
  { code: 'PB', name: 'Punjab' },
  { code: 'RJ', name: 'Rajasthan' },
  { code: 'SK', name: 'Sikkim' },
  { code: 'TN', name: 'Tamil Nadu' },
  { code: 'TG', name: 'Telangana' },
  { code: 'TR', name: 'Tripura' },
  { code: 'UP', name: 'Uttar Pradesh' },
  { code: 'UK', name: 'Uttarakhand' },
  { code: 'WB', name: 'West Bengal' },
]

export default function ProfilePage() {
  const [profile, setProfile] = useState(() => loadProfile())
  const [savedAt, setSavedAt] = useState('')
  const complete = useMemo(() => isProfileComplete(profile), [profile])
  const canMatch = complete

  function update(patch) {
    setProfile((p) => ({ ...p, ...patch }))
    setSavedAt('')
  }

  function onSubmit(e) {
    e.preventDefault()
    const next = {
      ...profile,
      domicileStates:
        profile.domicileStates.length > 0
          ? profile.domicileStates
          : profile.birthState
            ? [profile.birthState]
            : [],
    }
    setProfile(next)
    saveProfile(next)
    setSavedAt(new Date().toISOString())
  }

  function toggleDomicile(code) {
    const has = profile.domicileStates.includes(code)
    update({
      domicileStates: has
        ? profile.domicileStates.filter((c) => c !== code)
        : [...profile.domicileStates, code],
    })
  }

  return (
    <div className="section">
      <div className="container profile-layout">
        <div className="section-head">
          <div>
            <h1>Your profile</h1>
            <p className="muted">
              Stored only in this browser (<code>sarkari.profile.v1</code>). It is never saved on
              the server.
            </p>
          </div>
        </div>

        <form className="panel profile-form" onSubmit={onSubmit}>
          <label className="field">
            <span>Date of birth *</span>
            <input
              type="date"
              required
              value={profile.dob}
              onChange={(e) => update({ dob: e.target.value })}
            />
            <small className="muted">Age is computed on each notification’s as-on date, not today.</small>
          </label>

          <label className="field">
            <span>Highest education *</span>
            <select
              required
              value={profile.highestEducation}
              onChange={(e) => update({ highestEducation: e.target.value })}
            >
              <option value="">Select</option>
              {EDUCATION_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>

          <label className="field">
            <span>Discipline (optional)</span>
            <select
              value={profile.educationDiscipline}
              onChange={(e) => update({ educationDiscipline: e.target.value })}
            >
              {DISCIPLINE_OPTIONS.map((o) => (
                <option key={o.value || 'none'} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>

          <label className="field">
            <span>Reservation category *</span>
            <select
              required
              value={profile.reservationCategory}
              onChange={(e) => update({ reservationCategory: e.target.value })}
            >
              <option value="">Select category</option>
              {CATEGORY_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
            <small className="muted">
              Category is required to match. We only apply age relaxation when the official
              notification prints it for your category. We never invent relaxations. Reservation is
              required for age-relaxation tables.
            </small>
          </label>

          <label className="field">
            <span>Birth state / UT *</span>
            <select
              required
              value={profile.birthState}
              onChange={(e) => {
                const birthState = e.target.value
                const domicileStates =
                  profile.domicileStates.length === 0 && birthState
                    ? [birthState]
                    : profile.domicileStates
                update({ birthState, domicileStates })
              }}
            >
              <option value="">Select</option>
              {STATES.map((s) => (
                <option key={s.code} value={s.code}>
                  {s.name} ({s.code})
                </option>
              ))}
            </select>
          </label>

          <fieldset className="field">
            <legend>Domicile states *</legend>
            <p className="muted small">Select every state/UT you hold domicile in.</p>
            <div className="domicile-grid">
              {STATES.map((s) => (
                <label key={s.code} className="check-inline">
                  <input
                    type="checkbox"
                    checked={profile.domicileStates.includes(s.code)}
                    onChange={() => toggleDomicile(s.code)}
                  />
                  {s.code}
                </label>
              ))}
            </div>
          </fieldset>

          <label className="field">
            <span>Gender (optional)</span>
            <select value={profile.gender} onChange={(e) => update({ gender: e.target.value })}>
              <option value="">Prefer not to say</option>
              <option value="male">Male</option>
              <option value="female">Female</option>
              <option value="other">Other</option>
            </select>
          </label>

          <fieldset className="field">
            <legend>PwBD (optional)</legend>
            <label className="check-inline">
              <input
                type="checkbox"
                checked={Boolean(profile.pwbd?.hasDisability)}
                onChange={(e) =>
                  update({
                    pwbd: {
                      hasDisability: e.target.checked,
                      category: e.target.checked ? profile.pwbd?.category || 'OH' : 'none',
                    },
                  })
                }
              />
              I have a benchmark disability
            </label>
            {profile.pwbd?.hasDisability && (
              <label className="field" style={{ marginTop: '0.6rem' }}>
                <span>PwBD category</span>
                <select
                  value={profile.pwbd.category || 'OH'}
                  onChange={(e) =>
                    update({ pwbd: { ...profile.pwbd, category: e.target.value } })
                  }
                >
                  <option value="VH">VH</option>
                  <option value="HH">HH</option>
                  <option value="OH">OH</option>
                  <option value="others">Others</option>
                </select>
              </label>
            )}
            <small className="muted">
              When a notification lists posts with PwBD flags, match uses that list. If the list is
              incomplete, we stay at “verify on official” instead of claiming suitability.
            </small>
          </fieldset>

          <div className="hero-actions">
            <button type="submit" className="btn btn-primary">
              Save in this browser
            </button>
            <Link
              to="/match"
              className={`btn btn-secondary${canMatch ? '' : ' is-disabled'}`}
              aria-disabled={!canMatch}
              onClick={(e) => {
                if (!canMatch) e.preventDefault()
              }}
            >
              Match listed jobs
            </Link>
          </div>
          {savedAt && (
            <p className="muted small">
              Saved locally{complete ? '' : ' (complete required fields before matching)'}.
            </p>
          )}
          {!complete && (
            <p className="error-box">
              Complete date of birth, highest education, reservation category, birth state, and at
              least one domicile state before matching. Incomplete profiles are not sent to the
              match API.
            </p>
          )}
        </form>
      </div>
    </div>
  )
}
