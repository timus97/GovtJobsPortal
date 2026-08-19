export const PROFILE_KEY = 'sarkari.profile.v1'

export const emptyProfile = {
  dob: '',
  highestEducation: '',
  educationDiscipline: '',
  birthState: '',
  domicileStates: [],
  gender: '',
  pwbd: { hasDisability: false, category: 'none' },
  reservationCategory: '',
}

export function loadProfile() {
  try {
    const raw = localStorage.getItem(PROFILE_KEY)
    if (!raw) return { ...emptyProfile, pwbd: { ...emptyProfile.pwbd } }
    const parsed = JSON.parse(raw)
    return {
      ...emptyProfile,
      ...parsed,
      domicileStates: Array.isArray(parsed.domicileStates) ? parsed.domicileStates : [],
      pwbd: { ...emptyProfile.pwbd, ...(parsed.pwbd || {}) },
    }
  } catch {
    return { ...emptyProfile, pwbd: { ...emptyProfile.pwbd } }
  }
}

export function saveProfile(profile) {
  localStorage.setItem(PROFILE_KEY, JSON.stringify(profile))
}

export function isProfileComplete(profile) {
  return Boolean(
    profile?.dob &&
      profile?.highestEducation &&
      profile?.birthState &&
      Array.isArray(profile?.domicileStates) &&
      profile.domicileStates.length > 0 &&
      profile?.reservationCategory
  )
}
