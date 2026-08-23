import { Link } from 'react-router-dom'
import { useApi } from '../hooks/useApi'
import { listarPacientes } from '../api/pacientes'
import Table from '../components/ui/Table'
import Button from '../components/ui/Button'
import EmptyState from '../components/ui/EmptyState'
import Skeleton from '../components/ui/Skeleton'

export default function PacientesListPage() {
  const { data, loading, error } = useApi(listarPacientes, [])

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 style={{ fontFamily: 'var(--font-display)' }} className="text-2xl font-semibold text-[var(--text)]">
          Pacientes
        </h1>
        <Link to="/pacientes/nuevo">
          <Button>Nuevo paciente</Button>
        </Link>
      </div>

      {loading && <Skeleton rows={4} />}
      {error && <p className="text-[var(--danger)]">{error.message}</p>}
      {data?.length === 0 && <EmptyState>No hay pacientes registrados.</EmptyState>}

      {data?.length > 0 && (
        <Table>
          <thead>
            <tr className="border-b border-[var(--border)] text-[var(--text-muted)]">
              <th className="px-4 py-3 font-medium">Nombre</th>
              <th className="px-4 py-3 font-medium">RUT</th>
              <th className="px-4 py-3 font-medium">Email</th>
              <th className="px-4 py-3 font-medium">Teléfono</th>
            </tr>
          </thead>
          <tbody>
            {data.map((p) => (
              <tr key={p.id} className="border-b border-[var(--border)] last:border-0 hover:bg-[var(--surface-2)]">
                <td className="px-4 py-3">
                  <Link to={`/pacientes/${p.id}`} className="text-[var(--accent)] hover:underline">
                    {p.nombres} {p.apellidos}
                  </Link>
                </td>
                <td className="px-4 py-3">{p.rut}</td>
                <td className="px-4 py-3">{p.email}</td>
                <td className="px-4 py-3">{p.telefono}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </div>
  )
}
