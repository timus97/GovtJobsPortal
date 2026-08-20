const EVENT = 'student-session'

export function notifyStudentSession() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event(EVENT))
  }
}

export function onStudentSession(fn) {
  if (typeof window === 'undefined') return () => {}
  window.addEventListener(EVENT, fn)
  return () => window.removeEventListener(EVENT, fn)
}
