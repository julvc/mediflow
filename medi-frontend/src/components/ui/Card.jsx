export default function Card({ className = '', children }) {
  return (
    <div className={`rounded-xl border border-[var(--border)] bg-[var(--surface)] p-6 shadow-sm ${className}`}>
      {children}
    </div>
  )
}
