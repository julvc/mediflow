import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { ToastProvider } from './components/ui/Toast'
import AppShell from './components/layout/AppShell'
import LoginPage from './pages/LoginPage'
import RegistroPage from './pages/RegistroPage'
import AdminCrearUsuarioPage from './pages/AdminCrearUsuarioPage'
import AuditoriaPage from './pages/AuditoriaPage'
import ForbiddenPage from './pages/ForbiddenPage'
import NotFoundPage from './pages/NotFoundPage'
import DashboardPage from './pages/DashboardPage'
import ProfesionalesListPage from './pages/ProfesionalesListPage'
import ProfesionalDetailPage from './pages/ProfesionalDetailPage'
import ProfesionalFormPage from './pages/ProfesionalFormPage'
import PacientesListPage from './pages/PacientesListPage'
import PacienteDetailPage from './pages/PacienteDetailPage'
import PacienteFormPage from './pages/PacienteFormPage'
import TurnosPage from './pages/TurnosPage'
import TurnoDetailPage from './pages/TurnoDetailPage'
import TurnoFormPage from './pages/TurnoFormPage'

const STAFF = ['PROFESIONAL', 'ADMIN']

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/registro" element={<RegistroPage />} />

            <Route
              element={
                <ProtectedRoute>
                  <AppShell />
                </ProtectedRoute>
              }
            >
              <Route path="/" element={<DashboardPage />} />

              <Route path="/profesionales" element={<ProfesionalesListPage />} />
              <Route
                path="/profesionales/nuevo"
                element={
                  <ProtectedRoute roles={['ADMIN']}>
                    <ProfesionalFormPage />
                  </ProtectedRoute>
                }
              />
              <Route path="/profesionales/:id" element={<ProfesionalDetailPage />} />
              <Route path="/profesionales/:id/editar" element={<ProfesionalFormPage />} />

              <Route
                path="/pacientes"
                element={
                  <ProtectedRoute roles={STAFF}>
                    <PacientesListPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/pacientes/nuevo"
                element={
                  <ProtectedRoute roles={STAFF}>
                    <PacienteFormPage />
                  </ProtectedRoute>
                }
              />
              <Route path="/pacientes/:id" element={<PacienteDetailPage />} />
              <Route
                path="/pacientes/:id/editar"
                element={
                  <ProtectedRoute roles={STAFF}>
                    <PacienteFormPage />
                  </ProtectedRoute>
                }
              />

              <Route path="/turnos" element={<TurnosPage />} />
              <Route path="/turnos/nuevo" element={<TurnoFormPage />} />
              <Route path="/turnos/:id" element={<TurnoDetailPage />} />

              <Route
                path="/admin/usuarios/nuevo"
                element={
                  <ProtectedRoute roles={['ADMIN']}>
                    <AdminCrearUsuarioPage />
                  </ProtectedRoute>
                }
              />

              <Route
                path="/auditoria"
                element={
                  <ProtectedRoute roles={['ADMIN']}>
                    <AuditoriaPage />
                  </ProtectedRoute>
                }
              />

              <Route path="/403" element={<ForbiddenPage />} />
              <Route path="*" element={<NotFoundPage />} />
            </Route>
          </Routes>
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
