import { Link } from 'react-router-dom'
import Button from '../components/ui/Button'

export default function ForbiddenPage() {
  return (
    <div className="flex flex-col items-center gap-3 py-16 text-center">
      <h1 style={{ fontFamily: 'var(--font-display)' }} className="text-3xl font-semibold text-[var(--text)]">
        403 — Acceso denegado
      </h1>
      <p className="text-sm text-[var(--text-muted)]">No tienes permiso para ver esta página.</p>
      <Link to="/">
        <Button variant="secondary" className="mt-2">
          Volver al inicio
        </Button>
      </Link>
    </div>
  )
}
