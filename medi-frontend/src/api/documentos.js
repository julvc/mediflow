import { apiFetch } from './client'

export function listarDocumentosPorTurno(turnoId) {
  return apiFetch(`/documentos?turnoId=${turnoId}`)
}

export function crearDocumento(data) {
  return apiFetch('/documentos', { method: 'POST', body: JSON.stringify(data) })
}

export function cambiarEstadoDocumento(id, estado) {
  return apiFetch(`/documentos/${id}/estado`, { method: 'PATCH', body: JSON.stringify({ estado }) })
}

export function eliminarDocumento(id) {
  return apiFetch(`/documentos/${id}`, { method: 'DELETE' })
}
