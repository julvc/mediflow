// Espejo del mapa de transiciones de TurnoServiceImpl (backend) — solo para
// decidir que botones mostrar. El backend sigue siendo la autoridad real.
export const TRANSICIONES_VALIDAS = {
  PENDIENTE: ['CONFIRMADO', 'CANCELADO'],
  CONFIRMADO: ['COMPLETADO', 'CANCELADO'],
  CANCELADO: [],
  COMPLETADO: [],
}

// Un PACIENTE solo puede anular su propio turno (nunca confirmarlo ni completarlo
// — eso lo decide el centro medico). Espejo de la regla real en TurnoServiceImpl.
export const TRANSICIONES_PACIENTE = {
  PENDIENTE: ['CANCELADO'],
  CONFIRMADO: ['CANCELADO'],
  CANCELADO: [],
  COMPLETADO: [],
}

export const ESTADO_LABEL = {
  PENDIENTE: 'Pendiente',
  CONFIRMADO: 'Confirmado',
  CANCELADO: 'Cancelado',
  COMPLETADO: 'Completado',
}
