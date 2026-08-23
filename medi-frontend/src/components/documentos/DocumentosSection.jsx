import { useState } from 'react'
import { useApi } from '../../hooks/useApi'
import { cambiarEstadoDocumento, crearDocumento, eliminarDocumento, listarDocumentosPorTurno } from '../../api/documentos'
import { useAuth } from '../../auth/useAuth'
import Button from '../ui/Button'
import Input from '../ui/Input'
import Select from '../ui/Select'
import EstadoBadge from '../turnos/EstadoBadge'
import CambiarEstadoMenu from '../turnos/CambiarEstadoMenu'
import Skeleton from '../ui/Skeleton'
import { useToast } from '../ui/Toast'
import { ESTADO_LABEL, ESTILOS_BADGE, TRANSICIONES_VALIDAS } from '../../utils/estadoDocumento'

const VACIO = { nombreArchivo: '', tipoDocumento: '', urlStorage: '' }

const TIPOS_DOCUMENTO = [
  'Examen de laboratorio',
  'Receta médica',
  'Informe médico',
  'Imagenología',
  'Certificado médico',
  'Orden médica',
  'Epicrisis',
]

function DocumentoForm({ turnoId, onCreado }) {
  const [form, setForm] = useState(VACIO)
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  function onChange(campo) {
    return (e) => setForm((f) => ({ ...f, [campo]: e.target.value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const doc = await crearDocumento({ turnoId: Number(turnoId), ...form })
      setForm(VACIO)
      onCreado(doc)
    } catch (err) {
      setError(err)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="mb-4 flex flex-col gap-3 rounded-lg border border-[var(--border)] p-4">
      {error && (
        <div className="rounded-lg bg-[var(--danger-soft)] px-3 py-2 text-sm text-[var(--danger)]">
          <p>{error.message}</p>
          {error.detalles?.map((d) => (
            <p key={d}>{d}</p>
          ))}
        </div>
      )}
      <Input label="Nombre del archivo" required value={form.nombreArchivo} onChange={onChange('nombreArchivo')} />
      <Select label="Tipo de documento" required value={form.tipoDocumento} onChange={onChange('tipoDocumento')}>
        <option value="" disabled>
          Selecciona un tipo
        </option>
        {TIPOS_DOCUMENTO.map((t) => (
          <option key={t} value={t}>
            {t}
          </option>
        ))}
      </Select>
      <div>
        <Input label="URL del archivo" required value={form.urlStorage} onChange={onChange('urlStorage')} placeholder="https://..." />
        <p className="mt-1 text-xs text-[var(--text-muted)]">
          La carga real de archivos está pendiente (worker en Python) — por ahora ingresa una URL de referencia.
        </p>
      </div>
      <Button type="submit" disabled={submitting} className="self-start">
        {submitting ? 'Subiendo...' : 'Subir documento'}
      </Button>
    </form>
  )
}

export default function DocumentosSection({ turnoId }) {
  const { user } = useAuth()
  const toast = useToast()
  const esStaff = user.rol === 'PROFESIONAL' || user.rol === 'ADMIN'
  const { data, loading, error, refetch } = useApi(
    esStaff ? () => listarDocumentosPorTurno(turnoId) : () => Promise.resolve(null),
    [esStaff, turnoId],
  )
  const [recienCreado, setRecienCreado] = useState(null)
  const [mostrarForm, setMostrarForm] = useState(false)

  function handleCreado(doc) {
    setMostrarForm(false)
    if (esStaff) {
      toast('Documento subido')
      refetch()
    } else {
      setRecienCreado(doc)
    }
  }

  async function handleEliminar(id) {
    if (!window.confirm('¿Eliminar este documento? Esta acción no se puede deshacer.')) return
    await eliminarDocumento(id)
    toast('Documento eliminado')
    refetch()
  }

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-medium text-[var(--text-muted)]">Documentos</h2>
        <Button variant="secondary" onClick={() => setMostrarForm((v) => !v)}>
          {mostrarForm ? 'Cancelar' : 'Subir documento'}
        </Button>
      </div>

      {mostrarForm && <DocumentoForm turnoId={turnoId} onCreado={handleCreado} />}

      {esStaff && loading && <Skeleton rows={2} />}
      {esStaff && error && <p className="text-sm text-[var(--danger)]">{error.message}</p>}
      {esStaff && data?.length === 0 && !mostrarForm && (
        <p className="text-sm text-[var(--text-muted)]">Sin documentos para este turno.</p>
      )}

      {esStaff && data?.length > 0 && (
        <ul className="flex flex-col gap-2">
          {data.map((d) => (
            <li
              key={d.id}
              className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-[var(--border)] p-3 text-sm"
            >
              <div>
                <a href={d.urlStorage} target="_blank" rel="noreferrer" className="text-[var(--accent)] hover:underline">
                  {d.nombreArchivo}
                </a>
                <p className="text-[var(--text-muted)]">{d.tipoDocumento}</p>
              </div>
              <div className="flex items-center gap-2">
                <EstadoBadge estado={d.estado} labels={ESTADO_LABEL} estilos={ESTILOS_BADGE} />
                <CambiarEstadoMenu
                  estadoActual={d.estado}
                  labels={ESTADO_LABEL}
                  transiciones={TRANSICIONES_VALIDAS}
                  variantePorEstado={(estado) => (estado === 'ERROR' ? 'danger' : 'secondary')}
                  onCambiar={(nuevoEstado) =>
                    cambiarEstadoDocumento(d.id, nuevoEstado).then(() => {
                      toast('Estado del documento actualizado')
                      refetch()
                    })
                  }
                />
                {user.rol === 'ADMIN' && (
                  <Button variant="danger" onClick={() => handleEliminar(d.id)}>
                    Eliminar
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {!esStaff && recienCreado && (
        <div className="rounded-lg border border-[var(--accent)] bg-[var(--accent-soft)] p-3 text-sm">
          <p className="font-medium text-[var(--accent)]">Documento subido</p>
          <a href={recienCreado.urlStorage} target="_blank" rel="noreferrer" className="text-[var(--text)] hover:underline">
            {recienCreado.nombreArchivo}
          </a>
          <p className="mt-1 text-xs text-[var(--text-muted)]">
            No queda un listado de tus documentos disponible por ahora — guarda este enlace si lo necesitas de nuevo.
          </p>
        </div>
      )}
    </div>
  )
}
