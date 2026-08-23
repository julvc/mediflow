import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import Card from '../components/ui/Card'
import RegistroForm from '../components/auth/RegistroForm'

export default function RegistroPage() {
  const { login, user } = useAuth()
  const navigate = useNavigate()

  if (user) return <Navigate to="/" replace />

  async function handleExito(usuario, { email, password }) {
    await login(email, password)
    navigate('/', { replace: true })
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-[var(--bg)]">
      <Card className="w-full max-w-sm">
        <h1 className="mb-1 text-2xl font-semibold text-[var(--text)]">Crear cuenta</h1>
        <p className="mb-4 text-sm text-[var(--text-muted)]">
          Regístrate con el código que te entregó el centro médico
        </p>
        <RegistroForm rolFijo="PACIENTE" onExito={handleExito} />
        <p className="mt-4 text-center text-sm text-[var(--text-muted)]">
          ¿Ya tienes cuenta?{' '}
          <Link to="/login" className="text-[var(--accent)] hover:underline">
            Inicia sesión
          </Link>
        </p>
      </Card>
    </div>
  )
}
