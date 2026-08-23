import { useParams } from 'react-router-dom'
import { useApi } from '../hooks/useApi'
import { cambiarEstadoTurno, obtenerTurno } from '../api/turnos'
import { useAuth } from '../auth/useAuth'
import Card from '../components/ui/Card'
import EstadoBadge from '../components/turnos/EstadoBadge'
import CambiarEstadoMenu from '../components/turnos/CambiarEstadoMenu'
import DocumentosSection from '../components/documentos/DocumentosSection'
import Skeleton from '../components/ui/Skeleton'
import { useToast } from '../components/ui/Toast'
import { formatFechaHora } from '../utils/date'
import { TRANSICIONES_PACIENTE } from '../utils/estadoTurno'

export default function TurnoDetailPage() {
  const { id } = useParams()
  const { user } = useAuth()
  const toast = useToast()
  const { data: turno, loading, error, refetch } = useApi(() => obtenerTurno(id), [id])
  const esStaff = user.rol === 'PROFESIONAL' || user.rol === 'ADMIN'

  if (loading) return <Skeleton rows={4} />
  if (error) return <p className="text-[var(--danger)]">{error.message}</p>
  if (!turno) return null

  return (
    <div className="flex max-w-lg flex-col gap-4">
      <Card>
        <div className="mb-4 flex items-start justify-between">
          <div>
            <h1 style={{ fontFamily: 'var(--font-display)' }} className="text-2xl font-semibold text-[var(--text)]">
              {formatFechaHora(turno.fechaHora)}
            </h1>
            <p className="text-sm text-[var(--text-muted)]">{turno.motivo}</p>
          </div>
          <EstadoBadge estado={turno.estado} />
        </div>

        <dl className="mb-6 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
          <dt className="text-[var(--text-muted)]">Paciente</dt>
          <dd className="text-[var(--text)]">{turno.nombrePaciente}</dd>
          <dt className="text-[var(--text-muted)]">Profesional</dt>
          <dd className="text-[var(--text)]">{turno.nombreProfesional}</dd>
        </dl>

        <CambiarEstadoMenu
          estadoActual={turno.estado}
          transiciones={esStaff ? undefined : TRANSICIONES_PACIENTE}
          variantePorEstado={esStaff ? undefined : () => 'danger'}
          onCambiar={(nuevoEstado) =>
            cambiarEstadoTurno(id, nuevoEstado).then(() => {
              toast('Estado del turno actualizado')
              refetch()
            })
          }
        />
      </Card>

      <Card>
        <DocumentosSection turnoId={id} />
      </Card>
    </div>
  )
}
