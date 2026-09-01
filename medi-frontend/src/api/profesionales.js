import { apiFetch } from './client'

export function listarProfesionales() {
  return apiFetch('/profesionales')
}

export function obtenerProfesional(id) {
  return apiFetch(`/profesionales/${id}`)
}

export function crearProfesional(data) {
  return apiFetch('/profesionales', { method: 'POST', body: JSON.stringify(data) })
}

export function actualizarProfesional(id, data) {
  return apiFetch(`/profesionales/${id}`, { method: 'PUT', body: JSON.stringify(data) })
}
