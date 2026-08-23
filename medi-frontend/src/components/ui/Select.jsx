export default function Select({ label, id, className = '', children, ...props }) {
  return (
    <div className={className}>
      {label && (
        <label htmlFor={id} className="mb-1 block text-sm text-[var(--text)]">
          {label}
        </label>
      )}
      <select
        id={id}
        className="w-full rounded-lg border border-[var(--border)] bg-[var(--bg)] px-3 py-2 text-[var(--text)] focus:outline-none focus:ring-2 focus:ring-[var(--accent)]"
        {...props}
      >
        {children}
      </select>
    </div>
  )
}
