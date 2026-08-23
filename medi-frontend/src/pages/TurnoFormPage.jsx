import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { crearTurno } from '../api/turnos'
import { listarProfesionales } from '../api/profesionales'
import { listarPacientes } from '../api/pacientes'
import { useApi } from '../hooks/useApi'
import { useAuth } from '../auth/useAuth'
import Card from '../components/ui/Card'
import Input from '../components/ui/Input'
import Select from '../components/ui/Select'
import Button from '../components/ui/Button'
import { useToast } from '../components/ui/Toast'

export default function TurnoFormPage() {
  const { user } = useAuth()
  const esStaff = user.rol === 'PROFESIONAL' || user.rol === 'ADMIN'
  const navigate = useNavigate()
  const toast = useToast()

  const { data: profesionales } = useApi(listarProfesionales, [])
  // Un PACIENTE no tiene acceso a GET /pacientes (403) — ni siquiera se llama.
  const { data: pacientes } = useApi(esStaff ? listarPacientes : () => Promise.resolve([]), [esStaff])

  const [pacienteId, setPacienteId] = useState(esStaff ? '' : String(user.pacienteId))
  const [profesionalId, setProfesionalId] = useState('')
  const [fechaHora, setFechaHora] = useState('')
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const turno = await crearTurno({
        pacienteId: Number(pacienteId),
        profesionalId: Number(profesionalId),
        fechaHora: new Date(fechaHora).toISOString(),
        motivo,
      })
      toast('Turno agendado')
      navigate(`/turnos/${turno.id}`)
    } catch (err) {
      setError(err)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Card className="max-w-lg">
      <h1 style={{ fontFamily: 'var(--font-display)' }} className="mb-6 text-2xl font-semibold text-[var(--text)]">
        Agendar turno
      </h1>

      {error && (
        <div className="mb-4 rounded-lg bg-[var(--danger-soft)] px-3 py-2 text-sm text-[var(--danger)]">
          <p>{error.message}</p>
          {error.detalles?.map((d) => (
            <p key={d}>{d}</p>
          ))}
        </div>
      )}

      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {esStaff && (
          <Select label="Paciente" required value={pacienteId} onChange={(e) => setPacienteId(e.target.value)}>
            <option value="" disabled>
              Selecciona un paciente
            </option>
            {pacientes?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.nombres} {p.apellidos}
              </option>
            ))}
          </Select>
        )}

        <Select label="Profesional" required value={profesionalId} onChange={(e) => setProfesionalId(e.target.value)}>
          <option value="" disabled>
            Selecciona un profesional
          </option>
          {profesionales?.map((p) => (
            <option key={p.id} value={p.id}>
              {p.nombres} {p.apellidos} — {p.especialidad}
            </option>
          ))}
        </Select>

        <Input
          label="Fecha y hora"
          type="datetime-local"
          required
          value={fechaHora}
          onChange={(e) => setFechaHora(e.target.value)}
        />
        <Input label="Motivo" required value={motivo} onChange={(e) => setMotivo(e.target.value)} />

        <Button type="submit" disabled={submitting} className="mt-2">
          {submitting ? 'Agendando...' : 'Agendar'}
        </Button>
      </form>
    </Card>
  )
}
