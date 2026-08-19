import { Routes, Route } from 'react-router-dom'
import Layout from './components/Layout'
import HomePage from './pages/HomePage'
import JobsPage from './pages/JobsPage'
import JobDetailPage from './pages/JobDetailPage'
import ProcessPage from './pages/ProcessPage'
import SourcesPage from './pages/SourcesPage'
import AboutPage from './pages/AboutPage'
import ProfilePage from './pages/ProfilePage'
import MatchResultsPage from './pages/MatchResultsPage'
import { isProfileMatchEnabled } from './lib/features'

export default function App() {
  const profileMatch = isProfileMatchEnabled()
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="jobs" element={<JobsPage />} />
        <Route path="jobs/:id" element={<JobDetailPage />} />
        {profileMatch && <Route path="profile" element={<ProfilePage />} />}
        {profileMatch && <Route path="match" element={<MatchResultsPage />} />}
        <Route path="process" element={<ProcessPage />} />
        <Route path="sources" element={<SourcesPage />} />
        <Route path="about" element={<AboutPage />} />
      </Route>
    </Routes>
  )
}
