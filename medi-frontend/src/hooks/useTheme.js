import { useEffect, useState } from 'react'

const STORAGE_KEY = 'mediflow.theme'

function apply(theme) {
  if (theme) {
    document.documentElement.dataset.theme = theme
  } else {
    delete document.documentElement.dataset.theme
  }
}

export function useTheme() {
  const [theme, setTheme] = useState(() => {
    const stored = localStorage.getItem(STORAGE_KEY)
    apply(stored)
    return stored
  })

  useEffect(() => {
    apply(theme)
    if (theme) {
      localStorage.setItem(STORAGE_KEY, theme)
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  }, [theme])

  function toggle() {
    // Sistema -> claro -> oscuro -> sistema
    setTheme((t) => (t === null ? 'light' : t === 'light' ? 'dark' : null))
  }

  return { theme, toggle }
}
