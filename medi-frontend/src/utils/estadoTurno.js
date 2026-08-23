// Espejo del mapa de transiciones de TurnoServiceImpl (backend) — solo para
// decidir que botones mostrar. El backend sigue siendo la autoridad real.
export const TRANSICIONES_VALIDAS = {
  PENDIENTE: ['CONFIRMADO', 'CANCELADO'],
  CONFIRMADO: ['COMPLETADO', 'CANCELADO'],
  CANCELADO: [],
  COMPLETADO: [],
}

export const ESTADO_LABEL = {
  PENDIENTE: 'Pendiente',
  CONFIRMADO: 'Confirmado',
  CANCELADO: 'Cancelado',
  COMPLETADO: 'Completado',
}
