export default function Input({ label, id, className = '', ...props }) {
  return (
    <div className={className}>
      {label && (
        <label htmlFor={id} className="mb-1 block text-sm text-[var(--text)]">
          {label}
        </label>
      )}
      <input
        id={id}
        className="w-full rounded-lg border border-[var(--border)] bg-[var(--bg)] px-3 py-2 text-[var(--text)] focus:outline-none focus:ring-2 focus:ring-[var(--accent)]"
        {...props}
      />
    </div>
  )
}
