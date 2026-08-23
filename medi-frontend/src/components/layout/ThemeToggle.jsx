import { Moon, Sun, SunMoon } from 'lucide-react'
import { useTheme } from '../../hooks/useTheme'

const ICONO = { null: SunMoon, light: Sun, dark: Moon }
const ETIQUETA = { null: 'Tema del sistema', light: 'Tema claro', dark: 'Tema oscuro' }

export default function ThemeToggle() {
  const { theme, toggle } = useTheme()
  const Icono = ICONO[theme]

  return (
    <button
      onClick={toggle}
      title={`${ETIQUETA[theme]} — clic para cambiar`}
      aria-label={ETIQUETA[theme]}
      className="rounded-lg p-2 text-[var(--text-muted)] hover:bg-[var(--surface-2)] hover:text-[var(--text)]"
    >
      <Icono size={18} />
    </button>
  )
}
