const VARIANTES = {
  primary: 'bg-[var(--accent)] text-white hover:opacity-90',
  secondary: 'border border-[var(--border)] text-[var(--text)] hover:bg-[var(--surface-2)]',
  danger: 'bg-[var(--danger)] text-white hover:opacity-90',
}

export default function Button({ variant = 'primary', className = '', ...props }) {
  return (
    <button
      className={`inline-flex items-center justify-center rounded-lg px-4 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-60 ${VARIANTES[variant]} ${className}`}
      {...props}
    />
  )
}
