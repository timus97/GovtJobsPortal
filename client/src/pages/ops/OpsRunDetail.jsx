import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { me, probeOpsApi } from '../../api/ops'
import OpsRequiresApi from './OpsRequiresApi'

export default function OpsRunDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [apiOk, setApiOk] = useState(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    let cancelled = false
    async function boot() {
      const ok = await probeOpsApi()
      if (cancelled) return
      setApiOk(ok)
      if (!ok) return
      try {
        await me()
        if (!cancelled) setReady(true)
      } catch (err) {
        if (!cancelled && err.status === 401) {
          navigate('/ops/login', { replace: true })
        }
      }
    }
    boot()
    return () => {
      cancelled = true
    }
  }, [navigate])

  if (apiOk === null) {
    return (
      <div className="section">
        <div className="container">
          <p className="muted">Checking API host…</p>
        </div>
      </div>
    )
  }

  if (!apiOk) return <OpsRequiresApi />
  if (!ready) {
    return (
      <div className="section">
        <div className="container">
          <p className="muted">Loading session…</p>
        </div>
      </div>
    )
  }

  return (
    <div className="section">
      <div className="container">
        <p>
          <Link to="/ops" className="back-link">
            ← Back to ops
          </Link>
        </p>
        <div className="panel ops-card">
          <p className="eyebrow">Collect run</p>
          <h1>No run</h1>
          <p className="muted">
            There is no collect job <code>{id || '—'}</code> yet. Paste-URL collect and run
            timelines ship in PR08.
          </p>
        </div>
      </div>
    </div>
  )
}
