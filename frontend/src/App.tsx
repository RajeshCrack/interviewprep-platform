import { BrowserRouter, Navigate, NavLink, Outlet, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth'
import AuthPage from './pages/AuthPage'
import DashboardPage from './pages/DashboardPage'
import GoalPage from './pages/GoalPage'

function Shell() {
  const { user, ready, logout } = useAuth()
  if (!ready) return <p className="muted pad">Loading…</p>
  if (!user) return <Navigate to="/login" replace />
  return (
    <div className="shell">
      <nav className="nav" aria-label="Main">
        <p className="brand"><span className="logo" aria-hidden="true" />GateReady</p>
        <NavLink to="/" end>Home</NavLink>
        <NavLink to="/goals">Goals</NavLink>
        <button className="btn ghost" onClick={logout}>Log out</button>
      </nav>
      <main><Outlet /></main>
    </div>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<AuthPage mode="login" />} />
          <Route path="/register" element={<AuthPage mode="register" />} />
          <Route element={<Shell />}>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/goals" element={<GoalPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
