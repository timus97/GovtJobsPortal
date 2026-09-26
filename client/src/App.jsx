import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import PublicLayout from './components/PublicLayout'
import RequireAdmin from './components/RequireAdmin'
import RequireStudent from './components/RequireStudent'
import { isPrepareEnabled, isProfileMatchEnabled, isStudentEnabled } from './lib/features'
import AboutPage from './pages/AboutPage'
import AccountLoginPage from './pages/account/AccountLoginPage'
import AccountRegisterPage from './pages/account/AccountRegisterPage'
import AuthLandingPage from './pages/AuthLandingPage'
import ForgotPasswordPage from './pages/account/ForgotPasswordPage'
import ResetPasswordPage from './pages/account/ResetPasswordPage'
import DashboardPage from './pages/DashboardPage'
import DeskDetailPage from './pages/DeskDetailPage'
import HomePage from './pages/HomePage'
import JobDetailPage from './pages/JobDetailPage'
import JobsPage from './pages/JobsPage'
import MatchResultsPage from './pages/MatchResultsPage'
import MockPage from './pages/MockPage'
import MockReviewPage from './pages/MockReviewPage'
import AdminLogsPage from './pages/admin/AdminLogsPage'
import AdminSourceEditPage from './pages/admin/AdminSourceEditPage'
import AdminSourcesPage from './pages/admin/AdminSourcesPage'
import OpsDashboard from './pages/ops/OpsDashboard'
import OpsLoginPage from './pages/ops/OpsLoginPage'
import OpsReviewQueue from './pages/ops/OpsReviewQueue'
import OpsRunDetail from './pages/ops/OpsRunDetail'
import PreparePage from './pages/PreparePage'
import ProcessPage from './pages/ProcessPage'
import ProfilePage from './pages/ProfilePage'
import SourcesPage from './pages/SourcesPage'
import StudyPlanPage from './pages/StudyPlanPage'

export default function App() {
  const profileMatch = isProfileMatchEnabled()
  const prepare = isPrepareEnabled()
  const studentOn = isStudentEnabled()

  if (!studentOn) {
    return (
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<HomePage />} />
          <Route path="jobs" element={<JobsPage />} />
          <Route path="jobs/:id" element={<JobDetailPage />} />
          {profileMatch && <Route path="profile" element={<ProfilePage />} />}
          {profileMatch && <Route path="match" element={<MatchResultsPage />} />}
          {prepare && <Route path="prepare" element={<PreparePage />} />}
          <Route path="account/login" element={<AccountLoginPage />} />
          <Route path="account/register" element={<AccountRegisterPage />} />
          <Route path="account/forgot" element={<ForgotPasswordPage />} />
          <Route path="account/reset" element={<ResetPasswordPage />} />
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

  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route index element={<AuthLandingPage />} />
        <Route path="account/login" element={<AuthLandingPage mode="login" />} />
        <Route path="account/register" element={<AuthLandingPage mode="register" />} />
        <Route path="account/forgot" element={<ForgotPasswordPage />} />
        <Route path="account/reset" element={<ResetPasswordPage />} />
        <Route path="ops/login" element={<OpsLoginPage />} />
      </Route>

      <Route element={<RequireStudent />}>
        <Route path="profile" element={<ProfilePage />} />
        <Route path="match" element={<MatchResultsPage />} />
        <Route path="jobs" element={<JobsPage />} />
        <Route path="jobs/:id" element={<JobDetailPage />} />
        {prepare && <Route path="prepare" element={<PreparePage />} />}
        <Route path="dashboard" element={<DashboardPage />} />
        <Route path="desk/:id" element={<DeskDetailPage />} />
        <Route path="desk/:id/plan" element={<StudyPlanPage />} />
        <Route path="desk/:id/mock" element={<MockPage />} />
        <Route path="desk/:id/mock/:attemptId" element={<MockReviewPage />} />
        <Route path="process" element={<Navigate to="/match" replace />} />
        <Route path="sources" element={<Navigate to="/match" replace />} />
        <Route path="about" element={<Navigate to="/match" replace />} />
      </Route>

      <Route element={<RequireAdmin />}>
        <Route path="ops" element={<OpsDashboard />} />
        <Route path="ops/review" element={<OpsReviewQueue />} />
        <Route path="ops/runs/:id" element={<OpsRunDetail />} />
        <Route path="ops/sources" element={<AdminSourcesPage />} />
        <Route path="ops/sources/new" element={<AdminSourceEditPage />} />
        <Route path="ops/sources/:sourceId" element={<AdminSourceEditPage />} />
        <Route path="ops/logs" element={<AdminLogsPage />} />
        <Route path="ops/jobs" element={<JobsPage />} />
        <Route path="ops/jobs/:id" element={<JobDetailPage />} />
        {prepare && <Route path="ops/prepare" element={<PreparePage />} />}
        <Route path="ops/process" element={<ProcessPage />} />
        <Route path="ops/about" element={<AboutPage />} />
      </Route>
    </Routes>
  )
}
