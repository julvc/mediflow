function pad(n) {
  return String(n).padStart(2, '0')
}

// dd/mm/aaaa a mano en vez de Intl.DateTimeFormat('es-CL', {dateStyle:...}): ese
// locale de ICU separa con guiones ("23-08-2026"), no con "/" como se usa en Chile.
// hour12: false -> reloj de 24 horas ("14:30"). El propio punto del formato de
// 24 horas es no necesitar am/pm: ambos son mutuamente excluyentes, por eso no
// se muestra "p. m." junto a la hora en 24 horas.
export function formatFechaHora(iso) {
  const d = new Date(iso)
  const fecha = `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`
  const hora = new Intl.DateTimeFormat('es-CL', { timeStyle: 'short', hour12: false }).format(d)
  return `${fecha} ${hora}`
}

export function formatHora(iso) {
  return new Intl.DateTimeFormat('es-CL', { timeStyle: 'short', hour12: false }).format(new Date(iso))
}

// Recibe una fecha-only "aaaa-mm-dd" (LocalDate del backend, sin hora): se parsea
// como string, no con `new Date()`, para no arriesgar un corrimiento de dia por
// zona horaria (medianoche UTC cae el dia anterior en huso horario de Chile).
export function formatFecha(fechaISO) {
  const [anio, mes, dia] = fechaISO.split('-')
  return `${dia}/${mes}/${anio}`
}

// Clave local (no UTC) para agrupar turnos por dia de calendario — coincide
// con el dia que el usuario ve en su huso horario, igual que formatFechaHora.
export function claveDia(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
