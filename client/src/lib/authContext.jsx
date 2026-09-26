import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { onStudentSession } from './studentSession'

const BASE = import.meta.env.VITE_API_BASE || '/api'

async function fetchSession() {
  const res = await fetch(`${BASE}/session`, { credentials: 'include' })
  if (!res.ok) return { student: null, ops: null }
  return res.json()
}

const AuthContext = createContext({
  ready: false,
  student: null,
  ops: null,
  refresh: async () => {},
})

export function AuthProvider({ children }) {
  const [student, setStudent] = useState(null)
  const [ops, setOps] = useState(null)
  const [ready, setReady] = useState(false)

  async function refresh() {
    const body = await fetchSession()
    setStudent(body.student || null)
    setOps(body.ops || null)
  }

  useEffect(() => {
    let cancelled = false
    refresh()
      .catch(() => {
        if (!cancelled) {
          setStudent(null)
          setOps(null)
        }
      })
      .finally(() => {
        if (!cancelled) setReady(true)
      })
    const off = onStudentSession(() => {
      refresh().catch(() => {})
    })
    return () => {
      cancelled = true
      off()
    }
  }, [])

  const value = useMemo(() => ({ ready, student, ops, refresh, setStudent, setOps }), [ready, student, ops])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  return useContext(AuthContext)
}
