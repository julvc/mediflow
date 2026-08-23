import { NavLink } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'

const NAV_ITEMS = [
  { to: '/', label: () => 'Inicio', end: true },
  { to: '/turnos', label: (rol) => (rol === 'PACIENTE' ? 'Mis turnos' : 'Turnos') },
  { to: '/pacientes', label: () => 'Pacientes', roles: ['PROFESIONAL', 'ADMIN'] },
  { to: '/profesionales', label: () => 'Profesionales' },
]

export default function Sidebar() {
  const { user } = useAuth()
  const items = NAV_ITEMS.filter((item) => !item.roles || item.roles.includes(user.rol))

  return (
    <nav className="flex flex-col gap-1">
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          className={({ isActive }) =>
            `rounded-lg px-3 py-2 text-sm ${
              isActive
                ? 'bg-[var(--accent-soft)] text-[var(--accent)]'
                : 'text-[var(--text-muted)] hover:bg-[var(--surface-2)]'
            }`
          }
        >
          {item.label(user.rol)}
        </NavLink>
      ))}
    </nav>
  )
}
