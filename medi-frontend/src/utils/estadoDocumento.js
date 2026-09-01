// Espejo de DocumentoServiceImpl.TRANSICIONES_VALIDAS (backend) — solo para
// decidir que botones mostrar.
export const TRANSICIONES_VALIDAS = {
  PENDIENTE: ['PROCESADO', 'ERROR'],
  PROCESADO: [],
  ERROR: [],
}

export const ESTADO_LABEL = {
  PENDIENTE: 'Pendiente',
  PROCESADO: 'Procesado',
  ERROR: 'Error',
}

export const ESTILOS_BADGE = {
  PENDIENTE: 'bg-[var(--accent-soft)] text-[var(--warn)]',
  PROCESADO: 'bg-[var(--surface-2)] text-[var(--good)]',
  ERROR: 'bg-[var(--danger-soft)] text-[var(--danger)]',
}
