import { claveDia } from '../../utils/date'

const DIAS_SEMANA = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom']

function construirCeldas(mes) {
  const primerDia = new Date(mes.getFullYear(), mes.getMonth(), 1)
  const offset = (primerDia.getDay() + 6) % 7 // lunes = 0
  const diasEnMes = new Date(mes.getFullYear(), mes.getMonth() + 1, 0).getDate()

  const celdas = []
  for (let i = 0; i < offset; i++) celdas.push(null)
  for (let d = 1; d <= diasEnMes; d++) celdas.push(new Date(mes.getFullYear(), mes.getMonth(), d))
  while (celdas.length % 7 !== 0) celdas.push(null)
  return celdas
}

export default function MonthCalendar({ mes, turnos, diaSeleccionado, onSelectDia, onCambiarMes }) {
  const celdas = construirCeldas(mes)

  const porDia = {}
  for (const t of turnos) {
    const clave = claveDia(new Date(t.fechaHora))
    ;(porDia[clave] ??= []).push(t)
  }
  const hoy = claveDia(new Date())

  return (
    <div className="rounded-xl border border-[var(--border)] bg-[var(--surface)] p-4 shadow-sm">
      <div className="mb-4 flex items-center justify-between">
        <button
          type="button"
          onClick={() => onCambiarMes(-1)}
          className="rounded-lg px-2 py-1 text-[var(--text-muted)] hover:bg-[var(--surface-2)]"
          aria-label="Mes anterior"
        >
          ‹
        </button>
        <span style={{ fontFamily: 'var(--font-display)' }} className="text-lg font-semibold capitalize text-[var(--text)]">
          {new Intl.DateTimeFormat('es-CL', { month: 'long', year: 'numeric' }).format(mes)}
        </span>
        <button
          type="button"
          onClick={() => onCambiarMes(1)}
          className="rounded-lg px-2 py-1 text-[var(--text-muted)] hover:bg-[var(--surface-2)]"
          aria-label="Mes siguiente"
        >
          ›
        </button>
      </div>

      <div className="grid grid-cols-7 gap-1 text-center text-xs text-[var(--text-muted)]">
        {DIAS_SEMANA.map((d) => (
          <div key={d} className="py-1">
            {d}
          </div>
        ))}
      </div>

      <div className="grid grid-cols-7 gap-1">
        {celdas.map((dia, i) => {
          if (!dia) return <div key={i} />
          const clave = claveDia(dia)
          const turnosDia = porDia[clave] ?? []
          const esHoy = clave === hoy
          const seleccionado = diaSeleccionado && claveDia(diaSeleccionado) === clave

          return (
            <button
              key={i}
              type="button"
              onClick={() => onSelectDia(dia)}
              className={`flex min-h-16 flex-col items-start gap-1 rounded-lg border p-1.5 text-left text-sm transition ${
                seleccionado
                  ? 'border-[var(--accent)] bg-[var(--accent-soft)]'
                  : 'border-transparent hover:bg-[var(--surface-2)]'
              }`}
            >
              <span
                className={
                  esHoy
                    ? 'flex h-6 w-6 items-center justify-center rounded-full bg-[var(--accent)] text-white'
                    : 'text-[var(--text)]'
                }
              >
                {dia.getDate()}
              </span>
              {turnosDia.length > 0 && (
                <span className="text-xs text-[var(--accent-2)]">
                  {turnosDia.length} turno{turnosDia.length > 1 ? 's' : ''}
                </span>
              )}
            </button>
          )
        })}
      </div>
    </div>
  )
}
