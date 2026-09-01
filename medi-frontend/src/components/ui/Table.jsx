// Wrapper delgado: solo da el marco visual (borde, scroll). El thead/tbody
// los define cada pagina — abstraer las columnas es prematuro con una sola
// tabla hoy; se reevalua si una tercera entidad repite la misma estructura.
export default function Table({ children }) {
  return (
    <div className="overflow-x-auto rounded-xl border border-[var(--border)] bg-[var(--surface)]">
      <table className="w-full text-left text-sm">{children}</table>
    </div>
  )
}
