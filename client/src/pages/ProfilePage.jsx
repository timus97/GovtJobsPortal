import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import '../App.css'
import { isProfileComplete, loadProfile, saveProfile } from '../lib/profile'
import { isStudentEnabled } from '../lib/features'
import {
  getServerProfile,
  importServerProfile,
  meAccount,
  saveServerProfile,
} from '../api/account'

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
  const [student, setStudent] = useState(null)
  const [importOffer, setImportOffer] = useState(false)
  const [error, setError] = useState('')
  const complete = useMemo(() => isProfileComplete(profile), [profile])
  const canMatch = complete
  const studentOn = isStudentEnabled()

  useEffect(() => {
    if (!studentOn) return undefined
    let cancelled = false
    meAccount()
      .then(async (body) => {
        if (cancelled) return
        setStudent(body.student || null)
        const remote = await getServerProfile()
        if (cancelled) return
        if (remote.profile && (remote.profile.dob || remote.profile.reservationCategory)) {
          setProfile((p) => ({ ...p, ...remote.profile }))
          setImportOffer(false)
        } else {
          const local = loadProfile()
          setImportOffer(Boolean(local.dob || local.reservationCategory))
        }
      })
      .catch(() => {
        if (!cancelled) setStudent(null)
      })
    return () => {
      cancelled = true
    }
  }, [studentOn])

  function update(patch) {
    setProfile((p) => ({ ...p, ...patch }))
    setSavedAt('')
  }

  async function onSubmit(e) {
    e.preventDefault()
    setError('')
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
    if (student) {
      try {
        await saveServerProfile(next)
      } catch (err) {
        setError(err.message || 'Could not save on the server')
        return
      }
    }
    setSavedAt(new Date().toISOString())
  }

  async function onImport() {
    setError('')
    const local = loadProfile()
    try {
      const out = await importServerProfile(local)
      setProfile((p) => ({ ...p, ...out.profile }))
      setImportOffer(false)
      setSavedAt(new Date().toISOString())
    } catch (err) {
      setError(err.message || 'Import failed')
    }
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
              {student
                ? `Signed in as ${student.email}. Facts are saved on this API host for match and your desk.`
                : 'Stored in this browser until you sign in. Create an account to keep the profile on the API host.'}
            </p>
          </div>
        </div>

        {error && <p className="error-box">{error}</p>}
        {importOffer && student && (
          <div className="match-banner" role="note">
            This browser has a saved profile.{' '}
            <button type="button" className="btn btn-secondary" onClick={onImport}>
              Import browser profile
            </button>
          </div>
        )}
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
              {student ? 'Save profile' : 'Save in this browser'}
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
              Saved{student ? ' on this host' : ' locally'}
              {complete ? '' : ' (complete required fields before matching)'}.
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
