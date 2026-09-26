import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../lib/authContext'
import StudentLayout from './StudentLayout'

export default function RequireStudent() {
  const { ready, student, ops } = useAuth()
  const location = useLocation()

  if (!ready) {
    return (
      <div className="section">
        <div className="container">
          <p className="muted">Checking your session…</p>
        </div>
      </div>
    )
  }
  if (ops && !student) {
    return <Navigate to="/ops" replace />
  }
  if (!student) {
    const next = encodeURIComponent(`${location.pathname}${location.search}`)
    return <Navigate to={`/?next=${next}`} replace />
  }
  return <StudentLayout />
}
