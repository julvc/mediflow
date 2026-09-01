import { useState } from 'react'
import Card from '../components/ui/Card'
import RegistroForm from '../components/auth/RegistroForm'

export default function AdminCrearUsuarioPage() {
  const [creado, setCreado] = useState(null)

  async function handleExito(usuario) {
    setCreado(usuario)
  }

  return (
    <Card className="max-w-sm">
      <h1 style={{ fontFamily: 'var(--font-display)' }} className="mb-1 text-2xl font-semibold text-[var(--text)]">
        Crear cuenta
      </h1>
      <p className="mb-4 text-sm text-[var(--text-muted)]">
        Vincula un usuario a una ficha de paciente o profesional existente
      </p>

      {creado && (
        <div className="mb-4 rounded-lg border border-[var(--accent)] bg-[var(--accent-soft)] p-3 text-sm">
          <p className="font-medium text-[var(--accent)]">Cuenta creada</p>
          <p className="text-[var(--text)]">
            {creado.email} · {creado.rol}
          </p>
        </div>
      )}

      <RegistroForm onExito={handleExito} />
    </Card>
  )
}
