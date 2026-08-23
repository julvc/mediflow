import { Outlet } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import Sidebar from './Sidebar'

export default function AppShell() {
  const { user, logout } = useAuth()

  return (
    <div className="min-h-screen bg-[var(--bg)] text-[var(--text)]">
      <header className="flex items-center justify-between border-b border-[var(--border)] px-6 py-4">
        <span style={{ fontFamily: 'var(--font-display)' }} className="text-xl font-semibold">
          MediFlow
        </span>
        <div className="flex items-center gap-4 text-sm">
          <span>
            {user?.email} <span className="text-[var(--text-muted)]">({user?.rol})</span>
          </span>
          <button onClick={logout} className="text-[var(--accent)] hover:underline">
            Salir
          </button>
        </div>
      </header>
      <div className="flex">
        <aside className="w-56 shrink-0 border-r border-[var(--border)] p-4">
          <Sidebar />
        </aside>
        <main className="flex-1 p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
