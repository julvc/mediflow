import { useState } from 'react'
import Button from '../ui/Button'
import { ESTADO_LABEL as ESTADO_LABEL_TURNO, TRANSICIONES_VALIDAS as TRANSICIONES_TURNO } from '../../utils/estadoTurno'

// Generico a proposito, igual que EstadoBadge: Documentos reusa este menu
// pasando sus propios labels/transiciones en vez de duplicarlo.
export default function CambiarEstadoMenu({
  estadoActual,
  onCambiar,
  labels = ESTADO_LABEL_TURNO,
  transiciones = TRANSICIONES_TURNO,
  variantePorEstado = (estado) => (estado === 'CANCELADO' ? 'danger' : 'secondary'),
}) {
  const [cambiando, setCambiando] = useState(false)
  const opciones = transiciones[estadoActual] ?? []

  if (opciones.length === 0) return null

  async function handleClick(nuevoEstado) {
    setCambiando(true)
    try {
      await onCambiar(nuevoEstado)
    } finally {
      setCambiando(false)
    }
  }

  return (
    <div className="flex gap-2">
      {opciones.map((estado) => (
        <Button
          key={estado}
          variant={variantePorEstado(estado)}
          disabled={cambiando}
          onClick={() => handleClick(estado)}
        >
          Marcar {labels[estado]}
        </Button>
      ))}
    </div>
  )
}
