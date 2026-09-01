import { apiFetch } from './client'

export function listarTurnos(pacienteId) {
  const query = pacienteId ? `?pacienteId=${pacienteId}` : ''
  return apiFetch(`/turnos${query}`)
}

export function obtenerTurno(id) {
  return apiFetch(`/turnos/${id}`)
}

export function crearTurno(data) {
  return apiFetch('/turnos', { method: 'POST', body: JSON.stringify(data) })
}

export function cambiarEstadoTurno(id, estado) {
  return apiFetch(`/turnos/${id}/estado`, { method: 'PATCH', body: JSON.stringify({ estado }) })
}
