export function formatFechaHora(iso) {
  return new Intl.DateTimeFormat('es-CL', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}

export function formatHora(iso) {
  return new Intl.DateTimeFormat('es-CL', { timeStyle: 'short' }).format(new Date(iso))
}

// Clave local (no UTC) para agrupar turnos por dia de calendario — coincide
// con el dia que el usuario ve en su huso horario, igual que formatFechaHora.
export function claveDia(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
