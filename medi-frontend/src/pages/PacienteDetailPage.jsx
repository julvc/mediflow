import { Link, useNavigate, useParams } from 'react-router-dom'
import { useState } from 'react'
import { useApi } from '../hooks/useApi'
import { eliminarPaciente, obtenerPaciente } from '../api/pacientes'
import { useAuth } from '../auth/useAuth'
import Card from '../components/ui/Card'
import Button from '../components/ui/Button'

export default function PacienteDetailPage() {
  const { id } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const { data: paciente, loading, error } = useApi(() => obtenerPaciente(id), [id])
  const [eliminando, setEliminando] = useState(false)
  const [errorEliminar, setErrorEliminar] = useState(null)

  const esStaff = user.rol === 'PROFESIONAL' || user.rol === 'ADMIN'

  async function handleEliminar() {
    if (!window.confirm(`¿Eliminar a ${paciente.nombres} ${paciente.apellidos}? Esta acción no se puede deshacer.`)) {
      return
    }
    setEliminando(true)
    setErrorEliminar(null)
    try {
      await eliminarPaciente(id)
      navigate('/pacientes')
    } catch (err) {
      setErrorEliminar(err.message)
      setEliminando(false)
    }
  }

  if (loading) return <p className="text-[var(--text-muted)]">Cargando...</p>
  if (error) return <p className="text-[var(--danger)]">{error.message}</p>
  if (!paciente) return null

  return (
    <div className="flex max-w-lg flex-col gap-4">
      <Card>
        <div className="mb-4 flex items-start justify-between">
          <div>
            <h1 style={{ fontFamily: 'var(--font-display)' }} className="text-2xl font-semibold text-[var(--text)]">
              {paciente.nombres} {paciente.apellidos}
            </h1>
            <p className="text-sm text-[var(--text-muted)]">{paciente.rut}</p>
          </div>
          {esStaff && (
            <div className="flex gap-2">
              <Link to={`/pacientes/${id}/editar`}>
                <Button variant="secondary">Editar</Button>
              </Link>
              {user.rol === 'ADMIN' && (
                <Button variant="danger" onClick={handleEliminar} disabled={eliminando}>
                  {eliminando ? 'Eliminando...' : 'Eliminar'}
                </Button>
              )}
            </div>
          )}
        </div>

        {errorEliminar && (
          <p className="mb-4 rounded-lg bg-[var(--danger-soft)] px-3 py-2 text-sm text-[var(--danger)]">
            {errorEliminar}
          </p>
        )}

        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
          <dt className="text-[var(--text-muted)]">Email</dt>
          <dd className="text-[var(--text)]">{paciente.email}</dd>
          <dt className="text-[var(--text-muted)]">Teléfono</dt>
          <dd className="text-[var(--text)]">{paciente.telefono}</dd>
          <dt className="text-[var(--text-muted)]">Fecha de nacimiento</dt>
          <dd className="text-[var(--text)]">{paciente.fechaNacimiento}</dd>
        </dl>
      </Card>

      {esStaff && (
        <Card className="bg-[var(--accent-soft)]">
          <p className="text-sm font-medium text-[var(--accent)]">Código para registro: {paciente.id}</p>
          <p className="mt-1 text-sm text-[var(--text-muted)]">
            Entrégaselo al paciente junto con su email para que se registre en /registro.
          </p>
        </Card>
      )}
    </div>
  )
}
