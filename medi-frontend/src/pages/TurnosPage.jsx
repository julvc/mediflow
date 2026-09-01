import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useApi } from '../hooks/useApi'
import { listarTurnos } from '../api/turnos'
import { useAuth } from '../auth/useAuth'
import Table from '../components/ui/Table'
import Button from '../components/ui/Button'
import EmptyState from '../components/ui/EmptyState'
import Skeleton from '../components/ui/Skeleton'
import EstadoBadge from '../components/turnos/EstadoBadge'
import MonthCalendar from '../components/turnos/MonthCalendar'
import { claveDia, formatFechaHora, formatHora } from '../utils/date'

function inicioDeMes(date) {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

export default function TurnosPage() {
  const { user } = useAuth()
  const esStaff = user.rol === 'PROFESIONAL' || user.rol === 'ADMIN'
  const { data, loading, error } = useApi(
    () => listarTurnos(esStaff ? undefined : user.pacienteId),
    [esStaff, user.pacienteId],
  )

  const [vista, setVista] = useState('calendario')
  const [mes, setMes] = useState(() => inicioDeMes(new Date()))
  const [diaSeleccionado, setDiaSeleccionado] = useState(null)

  function cambiarMes(delta) {
    setMes((m) => new Date(m.getFullYear(), m.getMonth() + delta, 1))
    setDiaSeleccionado(null)
  }

  const turnosDelDia = useMemo(() => {
    if (!diaSeleccionado || !data) return []
    const clave = claveDia(diaSeleccionado)
    return data.filter((t) => claveDia(new Date(t.fechaHora)) === clave)
  }, [diaSeleccionado, data])

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <h1 style={{ fontFamily: 'var(--font-display)' }} className="text-2xl font-semibold text-[var(--text)]">
          {esStaff ? 'Turnos' : 'Mis turnos'}
        </h1>
        <div className="flex items-center gap-2">
          <div className="flex rounded-lg border border-[var(--border)] p-0.5">
            <button
              type="button"
              onClick={() => setVista('calendario')}
              className={`rounded-md px-3 py-1 text-sm ${
                vista === 'calendario' ? 'bg-[var(--accent-soft)] text-[var(--accent)]' : 'text-[var(--text-muted)]'
              }`}
            >
              Calendario
            </button>
            <button
              type="button"
              onClick={() => setVista('listado')}
              className={`rounded-md px-3 py-1 text-sm ${
                vista === 'listado' ? 'bg-[var(--accent-soft)] text-[var(--accent)]' : 'text-[var(--text-muted)]'
              }`}
            >
              Listado
            </button>
          </div>
          <Link to="/turnos/nuevo">
            <Button>Agendar turno</Button>
          </Link>
        </div>
      </div>

      {loading && <Skeleton rows={4} />}
      {error && <p className="text-[var(--danger)]">{error.message}</p>}
      {data?.length === 0 && <EmptyState>No hay turnos registrados.</EmptyState>}

      {data?.length > 0 && vista === 'calendario' && (
        <div className="grid gap-4 md:grid-cols-[1fr_320px]">
          <MonthCalendar
            mes={mes}
            turnos={data}
            diaSeleccionado={diaSeleccionado}
            onSelectDia={setDiaSeleccionado}
            onCambiarMes={cambiarMes}
          />
          <div className="rounded-xl border border-[var(--border)] bg-[var(--surface)] p-4 shadow-sm">
            <h2 className="mb-3 text-sm font-medium text-[var(--text-muted)]">
              {diaSeleccionado
                ? new Intl.DateTimeFormat('es-CL', { dateStyle: 'full' }).format(diaSeleccionado)
                : 'Selecciona un día'}
            </h2>
            {diaSeleccionado && turnosDelDia.length === 0 && (
              <p className="text-sm text-[var(--text-muted)]">Sin turnos ese día.</p>
            )}
            <ul className="flex flex-col gap-2">
              {turnosDelDia.map((t) => (
                <li key={t.id}>
                  <Link
                    to={`/turnos/${t.id}`}
                    className="flex items-center justify-between rounded-lg border border-[var(--border)] p-2 text-sm hover:bg-[var(--surface-2)]"
                  >
                    <div>
                      <p className="text-[var(--text)]">{formatHora(t.fechaHora)}</p>
                      <p className="text-[var(--text-muted)]">{esStaff ? t.nombrePaciente : t.nombreProfesional}</p>
                    </div>
                    <EstadoBadge estado={t.estado} />
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}

      {data?.length > 0 && vista === 'listado' && (
        <Table>
          <thead>
            <tr className="border-b border-[var(--border)] text-[var(--text-muted)]">
              <th className="px-4 py-3 font-medium">Fecha</th>
              {esStaff && <th className="px-4 py-3 font-medium">Paciente</th>}
              <th className="px-4 py-3 font-medium">Profesional</th>
              <th className="px-4 py-3 font-medium">Estado</th>
            </tr>
          </thead>
          <tbody>
            {data.map((t) => (
              <tr key={t.id} className="border-b border-[var(--border)] last:border-0 hover:bg-[var(--surface-2)]">
                <td className="px-4 py-3">
                  <Link to={`/turnos/${t.id}`} className="text-[var(--accent)] hover:underline">
                    {formatFechaHora(t.fechaHora)}
                  </Link>
                </td>
                {esStaff && <td className="px-4 py-3">{t.nombrePaciente}</td>}
                <td className="px-4 py-3">{t.nombreProfesional}</td>
                <td className="px-4 py-3">
                  <EstadoBadge estado={t.estado} />
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </div>
  )
}
