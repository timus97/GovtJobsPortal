import { Routes, Route } from 'react-router-dom'
import Layout from './components/Layout'
import HomePage from './pages/HomePage'
import JobsPage from './pages/JobsPage'
import JobDetailPage from './pages/JobDetailPage'
import ProcessPage from './pages/ProcessPage'
import SourcesPage from './pages/SourcesPage'
import AboutPage from './pages/AboutPage'
import OpsLoginPage from './pages/ops/OpsLoginPage'
import OpsDashboard from './pages/ops/OpsDashboard'
import OpsRunDetail from './pages/ops/OpsRunDetail'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="jobs" element={<JobsPage />} />
        <Route path="jobs/:id" element={<JobDetailPage />} />
        <Route path="process" element={<ProcessPage />} />
        <Route path="sources" element={<SourcesPage />} />
        <Route path="about" element={<AboutPage />} />
        <Route path="ops" element={<OpsDashboard />} />
        <Route path="ops/login" element={<OpsLoginPage />} />
        <Route path="ops/runs/:id" element={<OpsRunDetail />} />
      </Route>
    </Routes>
  )
}
