import { Outlet } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import Sidebar from './Sidebar'
import ThemeToggle from './ThemeToggle'

export default function AppShell() {
  const { user, logout } = useAuth()

  return (
    <div className="min-h-screen bg-[var(--bg)] text-[var(--text)]">
      <header className="flex items-center justify-between border-b border-[var(--border)] px-4 py-4 md:px-6">
        <span style={{ fontFamily: 'var(--font-display)' }} className="text-xl font-semibold">
          MediFlow
        </span>
        <div className="flex items-center gap-3 text-sm">
          <ThemeToggle />
          <span className="hidden sm:inline">
            {user?.email} <span className="text-[var(--text-muted)]">({user?.rol})</span>
          </span>
          <button onClick={logout} className="text-[var(--accent)] hover:underline">
            Salir
          </button>
        </div>
      </header>
      <div className="flex flex-col md:flex-row">
        <aside className="shrink-0 overflow-x-auto border-b border-[var(--border)] p-2 md:w-56 md:border-b-0 md:border-r md:p-4">
          <Sidebar />
        </aside>
        <main className="flex-1 p-4 md:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
