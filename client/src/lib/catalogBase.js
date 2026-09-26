import { useLocation } from 'react-router-dom'

export function useCatalogBase() {
  const { pathname } = useLocation()
  return pathname.startsWith('/ops') ? '/ops/jobs' : '/jobs'
}

export function jobPath(id, base = '/jobs') {
  return `${base}/${encodeURIComponent(id)}`
}
