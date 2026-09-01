import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { useApi } from '../hooks/useApi'
import { listarTurnos } from '../api/turnos'
import { listarPacientes } from '../api/pacientes'
import { listarProfesionales } from '../api/profesionales'
import Card from '../components/ui/Card'
import Button from '../components/ui/Button'
import EstadoBadge from '../components/turnos/EstadoBadge'
import { claveDia, formatFechaHora, formatHora } from '../utils/date'

function TurnoItem({ turno, subtitulo }) {
  return (
    <li>
      <Link
        to={`/turnos/${turno.id}`}
        className="flex items-center justify-between rounded-lg border border-[var(--border)] p-3 text-sm hover:bg-[var(--surface-2)]"
      >
        <div>
          <p className="text-[var(--text)]">{subtitulo}</p>
          <p className="text-[var(--text-muted)]">{turno.motivo}</p>
        </div>
        <EstadoBadge estado={turno.estado} />
      </Link>
    </li>
  )
}

export default function DashboardPage() {
  const { user } = useAuth()
  const esStaff = user.rol === 'PROFESIONAL' || user.rol === 'ADMIN'

  const { data: turnos } = useApi(
    () => listarTurnos(esStaff ? undefined : user.pacienteId),
    [esStaff, user.pacienteId],
  )
  const { data: pacientes } = useApi(esStaff ? listarPacientes : () => Promise.resolve([]), [esStaff])
  const { data: profesionales } = useApi(listarProfesionales, [])

  if (!esStaff) {
    const proximos = (turnos ?? [])
      .filter((t) => new Date(t.fechaHora) >= new Date() && t.estado !== 'CANCELADO')
      .sort((a, b) => new Date(a.fechaHora) - new Date(b.fechaHora))
      .slice(0, 5)

    return (
      <div className="flex flex-col gap-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 style={{ fontFamily: 'var(--font-display)' }} className="text-2xl font-semibold text-[var(--text)]">
            Hola, {user.email}
          </h1>
          <Link to="/turnos/nuevo">
            <Button>Agendar turno</Button>
          </Link>
        </div>

        <Card>
          <h2 className="mb-4 text-sm font-medium text-[var(--text-muted)]">Próximos turnos</h2>
          {proximos.length === 0 && (
            <p className="text-sm text-[var(--text-muted)]">No tienes turnos agendados.</p>
          )}
          <ul className="flex flex-col gap-2">
            {proximos.map((t) => (
              <TurnoItem key={t.id} turno={t} subtitulo={`${formatFechaHora(t.fechaHora)} — ${t.nombreProfesional}`} />
            ))}
          </ul>
        </Card>
      </div>
    )
  }

  const hoy = claveDia(new Date())
  const turnosHoy = (turnos ?? [])
    .filter((t) => claveDia(new Date(t.fechaHora)) === hoy)
    .sort((a, b) => new Date(a.fechaHora) - new Date(b.fechaHora))
  const pendientes = (turnos ?? []).filter((t) => t.estado === 'PENDIENTE')

  const stats = [
    { label: 'Turnos pendientes', valor: pendientes.length },
    { label: 'Turnos hoy', valor: turnosHoy.length },
    { label: 'Pacientes', valor: pacientes?.length ?? 0 },
    { label: 'Profesionales', valor: profesionales?.length ?? 0 },
  ]

  return (
    <div className="flex flex-col gap-6">
      <h1 style={{ fontFamily: 'var(--font-display)' }} className="text-2xl font-semibold text-[var(--text)]">
        Hola, {user.email}
      </h1>

      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        {stats.map((s) => (
          <Card key={s.label}>
            <p className="text-3xl font-semibold text-[var(--accent)]">{s.valor}</p>
            <p className="text-sm text-[var(--text-muted)]">{s.label}</p>
          </Card>
        ))}
      </div>

      <Card>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-medium text-[var(--text-muted)]">Agenda de hoy</h2>
          <Link to="/turnos" className="text-sm text-[var(--accent)] hover:underline">
            Ver calendario completo
          </Link>
        </div>
        {turnosHoy.length === 0 && <p className="text-sm text-[var(--text-muted)]">Sin turnos para hoy.</p>}
        <ul className="flex flex-col gap-2">
          {turnosHoy.map((t) => (
            <TurnoItem key={t.id} turno={t} subtitulo={`${formatHora(t.fechaHora)} — ${t.nombrePaciente}`} />
          ))}
        </ul>
      </Card>
    </div>
  )
}
