import { useState } from 'react'
import Input from '../ui/Input'
import Select from '../ui/Select'
import Button from '../ui/Button'
import { registrar } from '../../api/auth'

const VACIO = { email: '', password: '', rol: 'PACIENTE', vinculoId: '' }

export default function RegistroForm({ rolFijo, onExito }) {
  const [form, setForm] = useState(rolFijo ? { ...VACIO, rol: rolFijo } : VACIO)
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  const rol = rolFijo ?? form.rol
  const labelVinculo = rol === 'PACIENTE' ? 'Código de paciente' : rol === 'PROFESIONAL' ? 'Código de profesional' : null

  function onChange(campo) {
    return (e) => setForm((f) => ({ ...f, [campo]: e.target.value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const usuario = await registrar({
        email: form.email,
        password: form.password,
        rol,
        pacienteId: rol === 'PACIENTE' ? Number(form.vinculoId) : undefined,
        profesionalId: rol === 'PROFESIONAL' ? Number(form.vinculoId) : undefined,
      })
      const credenciales = { email: form.email, password: form.password }
      setForm(rolFijo ? { ...VACIO, rol: rolFijo } : VACIO)
      await onExito(usuario, credenciales)
    } catch (err) {
      setError(err)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      {error && (
        <div className="rounded-lg bg-[var(--danger-soft)] px-3 py-2 text-sm text-[var(--danger)]">
          <p>{error.message}</p>
          {error.detalles?.map((d) => (
            <p key={d}>{d}</p>
          ))}
        </div>
      )}

      <Input
        label="Email"
        id="email"
        type="email"
        required
        autoComplete="username"
        value={form.email}
        onChange={onChange('email')}
      />
      <Input
        label="Contraseña"
        id="password"
        type="password"
        required
        minLength={8}
        autoComplete="new-password"
        value={form.password}
        onChange={onChange('password')}
      />

      {!rolFijo && (
        <Select label="Rol" id="rol" value={form.rol} onChange={onChange('rol')}>
          <option value="PACIENTE">Paciente</option>
          <option value="PROFESIONAL">Profesional</option>
          <option value="ADMIN">Admin</option>
        </Select>
      )}

      {labelVinculo && (
        <div>
          <Input
            label={labelVinculo}
            id="vinculoId"
            type="number"
            min="1"
            required
            value={form.vinculoId}
            onChange={onChange('vinculoId')}
          />
          {rol === 'PACIENTE' && (
            <p className="mt-1 text-xs text-[var(--text-muted)]">
              Te lo entrega el centro médico al crear tu ficha.
            </p>
          )}
        </div>
      )}

      <Button type="submit" disabled={submitting} className="mt-2 w-full">
        {submitting ? 'Creando cuenta...' : 'Crear cuenta'}
      </Button>
    </form>
  )
}
