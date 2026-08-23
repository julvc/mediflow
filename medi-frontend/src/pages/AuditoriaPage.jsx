import { useApi } from '../hooks/useApi'
import { listarAuditoria } from '../api/auditoria'
import Table from '../components/ui/Table'
import EmptyState from '../components/ui/EmptyState'
import Skeleton from '../components/ui/Skeleton'
import { formatFechaHora } from '../utils/date'

const ACCION_LABEL = {
  CREAR: 'Creación',
  ACTUALIZAR: 'Actualización',
  ELIMINAR: 'Eliminación',
  CAMBIAR_ESTADO: 'Cambio de estado',
  LOGIN: 'Inicio de sesión',
  REGISTRO: 'Registro de cuenta',
}

const ENTIDAD_LABEL = {
  PACIENTE: 'Paciente',
  PROFESIONAL: 'Profesional',
  TURNO: 'Turno',
  DOCUMENTO: 'Documento',
  USUARIO: 'Usuario',
}

export default function AuditoriaPage() {
  const { data, loading, error } = useApi(listarAuditoria, [])

  return (
    <div>
      <div className="mb-6">
        <h1 style={{ fontFamily: 'var(--font-display)' }} className="text-2xl font-semibold text-[var(--text)]">
          Auditoría
        </h1>
        <p className="text-sm text-[var(--text-muted)]">Últimos 200 movimientos, para control interno.</p>
      </div>

      {loading && <Skeleton rows={6} />}
      {error && <p className="text-[var(--danger)]">{error.message}</p>}
      {data?.length === 0 && <EmptyState>Sin movimientos registrados.</EmptyState>}

      {data?.length > 0 && (
        <Table>
          <thead>
            <tr className="border-b border-[var(--border)] text-[var(--text-muted)]">
              <th className="px-4 py-3 font-medium">Fecha</th>
              <th className="px-4 py-3 font-medium">Usuario</th>
              <th className="px-4 py-3 font-medium">Acción</th>
              <th className="px-4 py-3 font-medium">Entidad</th>
              <th className="px-4 py-3 font-medium">Detalle</th>
            </tr>
          </thead>
          <tbody>
            {data.map((r) => (
              <tr key={r.id} className="border-b border-[var(--border)] last:border-0 hover:bg-[var(--surface-2)]">
                <td className="px-4 py-3 whitespace-nowrap">{formatFechaHora(r.creadoEn)}</td>
                <td className="px-4 py-3">{r.usuarioEmail}</td>
                <td className="px-4 py-3">{ACCION_LABEL[r.accion]}</td>
                <td className="px-4 py-3">
                  {ENTIDAD_LABEL[r.entidad]}
                  {r.entidadId != null ? ` #${r.entidadId}` : ''}
                </td>
                <td className="px-4 py-3 text-[var(--text-muted)]">{r.detalle}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </div>
  )
}
