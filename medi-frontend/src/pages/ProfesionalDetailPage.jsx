import { Link, useParams } from 'react-router-dom'
import { useApi } from '../hooks/useApi'
import { obtenerProfesional } from '../api/profesionales'
import { useAuth } from '../auth/useAuth'
import Card from '../components/ui/Card'
import Button from '../components/ui/Button'
import Skeleton from '../components/ui/Skeleton'

export default function ProfesionalDetailPage() {
  const { id } = useParams()
  const { user } = useAuth()
  const { data: profesional, loading, error } = useApi(() => obtenerProfesional(id), [id])

  const puedeEditar = user.rol === 'ADMIN' || String(user.profesionalId) === id

  if (loading) return <Skeleton rows={3} />
  if (error) return <p className="text-[var(--danger)]">{error.message}</p>
  if (!profesional) return null

  return (
    <Card className="max-w-lg">
      <div className="mb-4 flex items-start justify-between">
        <div>
          <h1 style={{ fontFamily: 'var(--font-display)' }} className="text-2xl font-semibold text-[var(--text)]">
            {profesional.nombres} {profesional.apellidos}
          </h1>
          <p className="text-sm text-[var(--text-muted)]">{profesional.especialidad}</p>
        </div>
        {puedeEditar && (
          <Link to={`/profesionales/${id}/editar`}>
            <Button variant="secondary">Editar</Button>
          </Link>
        )}
      </div>
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
        <dt className="text-[var(--text-muted)]">RUT</dt>
        <dd className="text-[var(--text)]">{profesional.rut}</dd>
        <dt className="text-[var(--text-muted)]">Email</dt>
        <dd className="text-[var(--text)]">{profesional.email}</dd>
      </dl>
    </Card>
  )
}
