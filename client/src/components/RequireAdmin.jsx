import { Navigate } from 'react-router-dom'
import { useAuth } from '../lib/authContext'
import AdminLayout from './AdminLayout'

export default function RequireAdmin() {
  const { ready, ops } = useAuth()

  if (!ready) {
    return (
      <div className="section">
        <div className="container">
          <p className="muted">Checking operator session…</p>
        </div>
      </div>
    )
  }
  if (!ops) {
    return <Navigate to="/ops/login" replace />
  }
  return <AdminLayout />
}
