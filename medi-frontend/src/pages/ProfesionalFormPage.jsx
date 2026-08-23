import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { actualizarProfesional, crearProfesional, obtenerProfesional } from '../api/profesionales'
import Card from '../components/ui/Card'
import Input from '../components/ui/Input'
import Select from '../components/ui/Select'
import Button from '../components/ui/Button'
import Skeleton from '../components/ui/Skeleton'
import { useToast } from '../components/ui/Toast'

const VACIO = { rut: '', nombres: '', apellidos: '', especialidad: '', email: '' }

const ESPECIALIDADES = [
  'Medicina general',
  'Medicina familiar',
  'Pediatría',
  'Ginecología y obstetricia',
  'Traumatología',
  'Kinesiología',
  'Cardiología',
  'Dermatología',
  'Psiquiatría',
  'Psicología',
  'Nutrición',
  'Odontología',
  'Oftalmología',
  'Otorrinolaringología',
  'Fonoaudiología',
  'Enfermería',
]

export default function ProfesionalFormPage() {
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
    obtenerProfesional(id)
      .then((p) =>
        setForm({ rut: p.rut, nombres: p.nombres, apellidos: p.apellidos, especialidad: p.especialidad, email: p.email }),
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
      const guardado = editando ? await actualizarProfesional(id, form) : await crearProfesional(form)
      toast(editando ? 'Profesional actualizado' : 'Profesional creado')
      navigate(`/profesionales/${guardado.id}`)
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
        {editando ? 'Editar profesional' : 'Nuevo profesional'}
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
        <Select label="Especialidad" required value={form.especialidad} onChange={onChange('especialidad')}>
          <option value="" disabled>
            Selecciona una especialidad
          </option>
          {ESPECIALIDADES.map((e) => (
            <option key={e} value={e}>
              {e}
            </option>
          ))}
        </Select>
        <Input label="Email" type="email" required value={form.email} onChange={onChange('email')} />
        <Button type="submit" disabled={submitting} className="mt-2">
          {submitting ? 'Guardando...' : 'Guardar'}
        </Button>
      </form>
    </Card>
  )
}
