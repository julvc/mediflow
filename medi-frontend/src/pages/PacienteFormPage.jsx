import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { actualizarPaciente, crearPaciente, obtenerPaciente } from '../api/pacientes'
import Card from '../components/ui/Card'
import Input from '../components/ui/Input'
import Button from '../components/ui/Button'
import Skeleton from '../components/ui/Skeleton'
import { useToast } from '../components/ui/Toast'

const VACIO = { rut: '', nombres: '', apellidos: '', fechaNacimiento: '', email: '', telefono: '' }

export default function PacienteFormPage() {
  const { id } = useParams()
  const editando = Boolean(id)
  const navigate = useNavigate()
  const toast = useToast()
  const [form, setForm] = useState(VACIO)
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [loading, setLoading] = useState(editando)

  useEffect(() => {
    if (!editando) return
    obtenerPaciente(id)
      .then((p) =>
        setForm({
          rut: p.rut,
          nombres: p.nombres,
          apellidos: p.apellidos,
          fechaNacimiento: p.fechaNacimiento,
          email: p.email,
          telefono: p.telefono,
        }),
      )
      .catch(setError)
      .finally(() => setLoading(false))
  }, [id, editando])

  function onChange(campo) {
    return (e) => setForm((f) => ({ ...f, [campo]: e.target.value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const guardado = editando ? await actualizarPaciente(id, form) : await crearPaciente(form)
      toast(editando ? 'Paciente actualizado' : 'Paciente creado')
      navigate(`/pacientes/${guardado.id}`)
    } catch (err) {
      setError(err)
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <Skeleton rows={5} />

  return (
    <Card className="max-w-lg">
      <h1 style={{ fontFamily: 'var(--font-display)' }} className="mb-6 text-2xl font-semibold text-[var(--text)]">
        {editando ? 'Editar paciente' : 'Nuevo paciente'}
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
        <Input label="RUT" required value={form.rut} onChange={onChange('rut')} />
        <Input label="Nombres" required value={form.nombres} onChange={onChange('nombres')} />
        <Input label="Apellidos" required value={form.apellidos} onChange={onChange('apellidos')} />
        <Input
          label="Fecha de nacimiento"
          type="date"
          required
          value={form.fechaNacimiento}
          onChange={onChange('fechaNacimiento')}
        />
        <Input label="Email" type="email" required value={form.email} onChange={onChange('email')} />
        <Input label="Teléfono" value={form.telefono} onChange={onChange('telefono')} />
        <Button type="submit" disabled={submitting} className="mt-2">
          {submitting ? 'Guardando...' : 'Guardar'}
        </Button>
      </form>
    </Card>
  )
}
