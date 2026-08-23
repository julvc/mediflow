import { ESTADO_LABEL as ESTADO_LABEL_TURNO } from '../../utils/estadoTurno'

// Generico a proposito: Documentos (paso 7) reusa este mismo badge pasando
// sus propios mapas de label/estilo en vez de duplicar el componente.
const ESTILOS_TURNO = {
  PENDIENTE: 'bg-[var(--accent-soft)] text-[var(--warn)]',
  CONFIRMADO: 'bg-[var(--accent-2-soft)] text-[var(--accent-2)]',
  CANCELADO: 'bg-[var(--danger-soft)] text-[var(--danger)]',
  COMPLETADO: 'bg-[var(--surface-2)] text-[var(--good)]',
}

export default function EstadoBadge({ estado, labels = ESTADO_LABEL_TURNO, estilos = ESTILOS_TURNO }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium ${estilos[estado]}`}>
      {labels[estado]}
    </span>
  )
}
