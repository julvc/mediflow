const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1'

export class ApiError extends Error {
  constructor(status, error, message, detalles) {
    super(message)
    this.status = status
    this.error = error
    this.detalles = detalles ?? []
  }
}

// Estado del cliente vive en el modulo (no en React) para que cualquier import
// de apiFetch use el mismo token sin pasar props por todos lados.
let accessToken = null
let refreshToken = null
let onSessionExpired = () => {}
let refreshInFlight = null

export function setTokens(tokens) {
  accessToken = tokens?.accessToken ?? null
  refreshToken = tokens?.refreshToken ?? null
}

export function setSessionExpiredHandler(fn) {
  onSessionExpired = fn
}

async function parseError(response) {
  const body = await response.json().catch(() => null)
  return new ApiError(
    response.status,
    body?.error ?? response.statusText,
    body?.message ?? 'Error de red',
    body?.detalles ?? [],
  )
}

// Un solo refresh en vuelo: el refresh token es de un solo uso, dos llamadas
// paralelas a /auth/refresh harian que la segunda reciba un token ya canjeado.
function doRefresh() {
  if (!refreshInFlight) {
    refreshInFlight = fetch(`${BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
      .then(async (res) => {
        if (!res.ok) throw await parseError(res)
        return res.json()
      })
      .finally(() => {
        refreshInFlight = null
      })
  }
  return refreshInFlight
}

export async function apiFetch(path, options = {}) {
  const isAuthEndpoint = path === '/auth/refresh' || path === '/auth/login'
  const headers = { 'Content-Type': 'application/json', ...options.headers }
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`

  let response = await fetch(`${BASE_URL}${path}`, { ...options, headers })

  if (response.status === 401 && !isAuthEndpoint && refreshToken) {
    try {
      const tokens = await doRefresh()
      setTokens(tokens)
      window.dispatchEvent(new CustomEvent('mediflow:tokens', { detail: tokens }))
      response = await fetch(`${BASE_URL}${path}`, {
        ...options,
        headers: { ...headers, Authorization: `Bearer ${tokens.accessToken}` },
      })
    } catch {
      setTokens(null)
      onSessionExpired()
      throw await parseError(response)
    }
  }

  if (!response.ok) throw await parseError(response)
  if (response.status === 204) return null
  return response.json()
}
