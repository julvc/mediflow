import { apiFetch } from './client'

export function registrar(data) {
  return apiFetch('/auth/registro', { method: 'POST', body: JSON.stringify(data) })
}
