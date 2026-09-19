// Formato del "resumen" copiable de una asignación (para pegar en Trello):
// agrupa por tarea y bandeja, comprime los slots en rangos, con encabezado
// de cliente/trimestre/total. Se usa al asignar (Asignar) y al revisar las
// asignaciones por cliente (MisDosimetros).

// Comprime una lista de slots en rangos legibles: [1,2,3,5] -> "1-3, 5".
export function comprimirRangos(slots) {
  const s = [...new Set(slots.filter((n) => n != null))].sort((a, b) => a - b)
  if (!s.length) return ''
  const partes = []
  let ini = s[0], prev = s[0]
  for (let i = 1; i < s.length; i++) {
    if (s[i] === prev + 1) { prev = s[i]; continue }
    partes.push(ini === prev ? `${ini}` : `${ini}-${prev}`)
    ini = prev = s[i]
  }
  partes.push(ini === prev ? `${ini}` : `${ini}-${prev}`)
  return partes.join(', ')
}

export function construirResumenTexto(asignaciones) {
  if (!asignaciones?.length) return ''
  const a0 = asignaciones[0]
  const grupos = new Map()
  for (const a of asignaciones) {
    const tarea = a.numeroTarea || 'Sin tarea'
    const bandeja = a.numeroBandeja ?? null
    const clave = `${tarea}||${bandeja ?? 'sb'}`
    if (!grupos.has(clave)) grupos.set(clave, { tarea, bandeja, slots: [] })
    if (a.slotBandeja != null) grupos.get(clave).slots.push(a.slotBandeja)
  }
  const arr = [...grupos.values()].sort((x, y) => {
    const tx = Number(x.tarea) || 0, ty = Number(y.tarea) || 0
    if (tx !== ty) return tx - ty
    return (x.bandeja ?? -1) - (y.bandeja ?? -1)
  })
  const lineas = arr.map((g) => {
    const band = g.bandeja != null ? `Bandeja ${g.bandeja}` : 'Sin bandeja'
    const rangos = comprimirRangos(g.slots)
    return `- Tarea ${g.tarea} · ${band}${rangos ? `: slots ${rangos}` : ''}`
  })
  return [
    `Cliente: ${a0.clienteNombre} — Trimestre: ${a0.trimestre}`,
    `Dosímetros asignados: ${asignaciones.length}`,
    'Tareas ocupadas:',
    ...lineas,
  ].join('\n')
}
