import { createContext, useEffect, useRef, useState } from 'react'
import { apiFetch, setTokens, setSessionExpiredHandler } from '../api/client'
import { decodeJwt } from './jwt'

export const AuthContext = createContext(null)

const STORAGE_KEY = 'mediflow.auth'

function loadStoredTokens() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY))
  } catch {
    return null
  }
}

export function AuthProvider({ children }) {
  const [tokens, setTokensState] = useState(loadStoredTokens)
  const [loading, setLoading] = useState(true)
  const refreshTimer = useRef(null)

  const user = tokens ? decodeJwt(tokens.accessToken) : null

  function persist(newTokens) {
    setTokensState(newTokens)
    setTokens(newTokens)
    clearTimeout(refreshTimer.current)
    if (newTokens) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(newTokens))
      scheduleRefresh(newTokens)
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  }

  // Refresh proactivo ~60s antes de que expire el access token (dura 15 min) —
  // el retry reactivo al 401 dentro de apiFetch queda como red de seguridad,
  // no como el mecanismo que el usuario nota mientras llena un formulario.
  function scheduleRefresh(currentTokens) {
    const { exp } = decodeJwt(currentTokens.accessToken)
    const msUntilRefresh = exp * 1000 - Date.now() - 60_000
    if (msUntilRefresh <= 0) return
    refreshTimer.current = setTimeout(async () => {
      try {
        const fresh = await apiFetch('/auth/refresh', {
          method: 'POST',
          body: JSON.stringify({ refreshToken: currentTokens.refreshToken }),
        })
        persist(fresh)
      } catch {
        persist(null)
      }
    }, msUntilRefresh)
  }

  useEffect(() => {
    setSessionExpiredHandler(() => persist(null))

    const stored = loadStoredTokens()
    if (stored) {
      const { exp } = decodeJwt(stored.accessToken)
      if (exp * 1000 <= Date.now()) {
        persist(null)
      } else {
        setTokens(stored)
        scheduleRefresh(stored)
      }
    }
    setLoading(false)

    function onTokensRefreshedElsewhere(e) {
      persist(e.detail)
    }
    window.addEventListener('mediflow:tokens', onTokensRefreshedElsewhere)
    return () => {
      window.removeEventListener('mediflow:tokens', onTokensRefreshedElsewhere)
      clearTimeout(refreshTimer.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function login(email, password) {
    const data = await apiFetch('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    persist(data)
  }

  async function logout() {
    if (tokens) {
      try {
        await apiFetch('/auth/logout', {
          method: 'POST',
          body: JSON.stringify({ refreshToken: tokens.refreshToken }),
        })
      } catch {
        // best-effort: igual limpiamos la sesion local
      }
    }
    persist(null)
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}
