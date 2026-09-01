import { apiFetch } from './client'

export function listarAuditoria() {
  return apiFetch('/auditoria')
}
