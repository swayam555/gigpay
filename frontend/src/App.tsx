import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { MotionConfig } from 'motion/react'
import { useEffect } from 'react'
import { AuthProvider, useAuth } from './auth'
import WorkerLayout from './components/WorkerLayout'
import Landing from './landing/Landing'
import Login from './pages/Login'
import Home from './pages/Home'
import Trips from './pages/Trips'
import WalletPage from './pages/WalletPage'
import Coach from './pages/Coach'
import FinanceConsole from './finance/FinanceConsole'

function RequireWorker({ children }: { children: React.ReactNode }) {
  return useAuth().worker ? children : <Navigate to="/login" replace />
}

function ScrollToTop() {
  const { pathname, hash } = useLocation()
  useEffect(() => { if (!hash) window.scrollTo(0, 0) }, [pathname, hash])
  return null
}

export default function App() {
  return (
    <MotionConfig reducedMotion="user">
      <AuthProvider>
        <BrowserRouter>
          <ScrollToTop />
          <Routes>
            <Route path="/" element={<Landing />} />
            <Route path="/login" element={<Login />} />
            <Route path="/finance" element={<FinanceConsole />} />
            <Route path="/app" element={<RequireWorker><WorkerLayout /></RequireWorker>}>
              <Route index element={<Home />} />
              <Route path="trips" element={<Trips />} />
              <Route path="wallet" element={<WalletPage />} />
              <Route path="coach" element={<Coach />} />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </MotionConfig>
  )
}
