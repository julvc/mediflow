export default function Skeleton({ rows = 3, className = '' }) {
  return (
    <div className={`flex flex-col gap-2 ${className}`} role="status" aria-label="Cargando">
      {Array.from({ length: rows }).map((_, i) => (
        <div
          key={i}
          className="h-4 animate-pulse rounded bg-[var(--surface-2)]"
          style={{ width: `${100 - i * 15}%` }}
        />
      ))}
    </div>
  )
}
