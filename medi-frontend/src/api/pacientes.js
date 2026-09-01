import { apiFetch } from './client'

export function listarPacientes() {
  return apiFetch('/pacientes')
}

export function obtenerPaciente(id) {
  return apiFetch(`/pacientes/${id}`)
}

export function crearPaciente(data) {
  return apiFetch('/pacientes', { method: 'POST', body: JSON.stringify(data) })
}

export function actualizarPaciente(id, data) {
  return apiFetch(`/pacientes/${id}`, { method: 'PUT', body: JSON.stringify(data) })
}

export function eliminarPaciente(id) {
  return apiFetch(`/pacientes/${id}`, { method: 'DELETE' })
}
