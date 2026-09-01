import { Link } from 'react-router-dom'
import Button from '../components/ui/Button'

export default function NotFoundPage() {
  return (
    <div className="flex flex-col items-center gap-3 py-16 text-center">
      <h1 style={{ fontFamily: 'var(--font-display)' }} className="text-3xl font-semibold text-[var(--text)]">
        404 — Página no encontrada
      </h1>
      <p className="text-sm text-[var(--text-muted)]">La página que buscas no existe.</p>
      <Link to="/">
        <Button variant="secondary" className="mt-2">
          Volver al inicio
        </Button>
      </Link>
    </div>
  )
}
