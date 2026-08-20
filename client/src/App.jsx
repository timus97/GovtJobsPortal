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
import OpsLoginPage from './pages/ops/OpsLoginPage'
import OpsDashboard from './pages/ops/OpsDashboard'
import OpsRunDetail from './pages/ops/OpsRunDetail'
import OpsReviewQueue from './pages/ops/OpsReviewQueue'
import PreparePage from './pages/PreparePage'
import AccountLoginPage from './pages/account/AccountLoginPage'
import AccountRegisterPage from './pages/account/AccountRegisterPage'
import DashboardPage from './pages/DashboardPage'
import DeskDetailPage from './pages/DeskDetailPage'
import { isPrepareEnabled, isProfileMatchEnabled, isStudentEnabled } from './lib/features'

export default function App() {
  const profileMatch = isProfileMatchEnabled()
  const prepare = isPrepareEnabled()
  const student = isStudentEnabled()
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="jobs" element={<JobsPage />} />
        <Route path="jobs/:id" element={<JobDetailPage />} />
        {profileMatch && <Route path="profile" element={<ProfilePage />} />}
        {profileMatch && <Route path="match" element={<MatchResultsPage />} />}
        {prepare && <Route path="prepare" element={<PreparePage />} />}
        {student && <Route path="account/login" element={<AccountLoginPage />} />}
        {student && <Route path="account/register" element={<AccountRegisterPage />} />}
        {student && <Route path="dashboard" element={<DashboardPage />} />}
        {student && <Route path="desk/:id" element={<DeskDetailPage />} />}
        <Route path="process" element={<ProcessPage />} />
        <Route path="sources" element={<SourcesPage />} />
        <Route path="about" element={<AboutPage />} />
        <Route path="ops" element={<OpsDashboard />} />
        <Route path="ops/login" element={<OpsLoginPage />} />
        <Route path="ops/runs/:id" element={<OpsRunDetail />} />
        <Route path="ops/review" element={<OpsReviewQueue />} />
      </Route>
    </Routes>
  )
}
