import { useEffect, useMemo, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import {
  getEjecutivos,
  getResumenClienteTrimestre,
  getMisResumenClienteTrimestre,
} from '../api/endpoints'
import { Card, Select, Input, Button, Badge, Loading, EmptyState, Pagination } from '../components/ui'
import { useToast } from '../components/Toast'

const POR_PAGINA = 10

export default function PendienteAsignacion() {
  const { rol } = useAuth()
  const esEjecutivo = rol === 'EJECUTIVO'
  const toast = useToast()

  const [conteos, setConteos] = useState([])
  const [ejecutivos, setEjecutivos] = useState([])
  const [ejecutivoId, setEjecutivoId] = useState('')
  const [trimestresSel, setTrimestresSel] = useState(() => new Set()) // vacío = todos (los disponibles)
  const [busqueda, setBusqueda] = useState('')
  const [soloPendientes, setSoloPendientes] = useState(false)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!esEjecutivo) getEjecutivos().then(setEjecutivos).catch(() => {})
  }, [esEjecutivo])

  const cargar = () => {
    setLoading(true)
    const pConteos = esEjecutivo
      ? getMisResumenClienteTrimestre()
      : getResumenClienteTrimestre(ejecutivoId ? { ejecutivoId } : undefined)
    pConteos
      .then(setConteos)
      .catch(() => toast.error('No se pudo cargar la información'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    cargar()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ejecutivoId])

  // Lookup clienteId -> { trimestre -> cantidad }
  const porCliente = useMemo(() => {
    const map = new Map()
    for (const c of conteos) {
      if (!map.has(c.clienteId)) map.set(c.clienteId, {})
      map.get(c.clienteId)[c.trimestre] = c.cantidad
    }
    return map
  }, [conteos])

  // Clientes que aparecen en los conteos = clientes a los que el ejecutivo
  // realmente ASIGNÓ (no el responsable actual del cliente). Así un cliente que
  // cambió de ejecutivo no desaparece del ejecutivo que sí le asignó ese
  // trimestre.
  const clientesDeConteo = useMemo(() => {
    const map = new Map()
    for (const c of conteos) {
      if (!map.has(c.clienteId)) {
        map.set(c.clienteId, { id: c.clienteId, razonSocial: c.razonSocial })
      }
    }
    return [...map.values()]
  }, [conteos])

  // Trimestres presentes en los datos (orden descendente por año y trimestre).
  const trimestresDisponibles = useMemo(() => {
    const set = new Set(conteos.map((c) => c.trimestre))
    return [...set].sort((a, b) => {
      const ay = a.slice(2), by = b.slice(2)
      if (ay !== by) return by.localeCompare(ay)
      return b.slice(0, 1).localeCompare(a.slice(0, 1))
    })
  }, [conteos])

  // Columnas a comparar: las seleccionadas, o todas si no hay selección.
  const columnas = useMemo(() => {
    const base = trimestresSel.size > 0
      ? trimestresDisponibles.filter((t) => trimestresSel.has(t))
      : trimestresDisponibles
    return base
  }, [trimestresDisponibles, trimestresSel])

  const toggleTrimestre = (t) => setTrimestresSel((prev) => {
    const next = new Set(prev)
    if (next.has(t)) next.delete(t); else next.add(t)
    return next
  })

  // Trimestre "base" de comparación = el más antiguo de los seleccionados;
  // el "actual" = el más reciente. Solo se compara cuando el usuario elige
  // trimestres (si no elige, se muestran todos los clientes).
  const comparando = trimestresSel.size >= 1
  const base = comparando && columnas.length ? columnas[columnas.length - 1] : null
  const colActual = columnas.length ? columnas[0] : null

  // Filas: al comparar, solo los clientes que tuvieron asignación en el
  // trimestre base (para contrastar el período anterior con el actual),
  // ordenados por cantidad de mayor a menor.
  const filas = useMemo(() => {
    const q = busqueda.trim().toLowerCase()
    let lista = clientesDeConteo
    if (q) lista = lista.filter((c) => c.razonSocial.toLowerCase().includes(q))
    // Excluye a los clientes que no tuvieron asignación en el trimestre base.
    if (base) lista = lista.filter((c) => (porCliente.get(c.id)?.[base] || 0) > 0)
    // Opcional: solo los que hoy están pendientes en el trimestre actual.
    if (soloPendientes && colActual) {
      lista = lista.filter((c) => !((porCliente.get(c.id)?.[colActual] || 0) > 0))
    }
    // Orden por cantidad del trimestre base (o el actual si no hay base), desc.
    const ref = base || colActual
    return [...lista].sort((a, b) => {
      const ca = ref ? (porCliente.get(a.id)?.[ref] || 0) : 0
      const cb = ref ? (porCliente.get(b.id)?.[ref] || 0) : 0
      if (cb !== ca) return cb - ca
      return a.razonSocial.localeCompare(b.razonSocial)
    })
  }, [clientesDeConteo, busqueda, soloPendientes, base, colActual, porCliente])

  useEffect(() => { setPage(1) }, [busqueda, soloPendientes, trimestresSel, ejecutivoId])

  const totalPages = Math.ceil(filas.length / POR_PAGINA)
  const visibles = filas.slice((page - 1) * POR_PAGINA, page * POR_PAGINA)

  // Exporta la tabla tal como se ve, respetando los filtros seleccionados
  // (búsqueda, ejecutivo, trimestres a comparar y "solo pendientes"). Se
  // exportan TODAS las filas filtradas, no solo la página visible.
  const exportarClientes = () => {
    if (columnas.length === 0 || filas.length === 0) {
      toast.error('No hay datos para exportar con los filtros actuales')
      return
    }
    const sep = ';' // separador de lista habitual en Excel en español
    const esc = (v) => {
      const s = String(v ?? '')
      return /[";\n\r]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s
    }
    const encabezados = ['Cliente', ...columnas]
    const lineas = filas.map((c) => {
      const conteo = porCliente.get(c.id) || {}
      const celdas = [c.razonSocial, ...columnas.map((t) => {
        const n = conteo[t] || 0
        return n > 0 ? n : 'Pendiente'
      })]
      return celdas.map(esc).join(sep)
    })
    const csv = '﻿' + [encabezados.map(esc).join(sep), ...lineas].join('\r\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
    const ejec = ejecutivos.find((e) => String(e.id) === String(ejecutivoId))
    const sufijo = ejec ? '_' + ejec.nombre.replace(/[^a-zA-Z0-9]+/g, '-') : ''
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `pendiente_asignacion${sufijo}_${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    URL.revokeObjectURL(url)
    toast.success(`Exportados ${filas.length} clientes`)
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-ink">Pendiente de asignación</h1>
        <p className="text-sm text-slate-500 mt-0.5">
          Selecciona los <b>trimestres a comparar</b>. Se muestran solo los clientes que tuvieron
          asignación en el <b>trimestre base</b> (el más antiguo elegido), ordenados por cantidad
          (de mayor a menor). Donde no hay asignación en el período, el cliente está <b>pendiente</b>.
        </p>
      </div>

      <Card title="Filtros">
        <div className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            <Input label="Buscar cliente" value={busqueda} onChange={(e) => setBusqueda(e.target.value)} placeholder="Razón social…" />
            {!esEjecutivo && (
              <Select label="Ejecutivo" value={ejecutivoId} onChange={(e) => setEjecutivoId(e.target.value)}>
                <option value="">Todos los ejecutivos</option>
                {ejecutivos.map((ej) => (
                  <option key={ej.id} value={ej.id}>{ej.nombre}</option>
                ))}
              </Select>
            )}
          </div>
          <div>
            <div className="flex items-center justify-between mb-2">
              <p className="text-xs font-semibold uppercase tracking-wide text-ink/50">Trimestres a comparar</p>
              <span className="text-xs text-ink/40">{trimestresSel.size === 0 ? 'Todos' : `${trimestresSel.size} seleccionados`}</span>
            </div>
            {trimestresDisponibles.length === 0 ? (
              <p className="text-sm text-slate-400">Sin trimestres con asignaciones</p>
            ) : (
              <div className="flex flex-wrap gap-2">
                {trimestresDisponibles.map((t) => (
                  <button key={t} type="button" onClick={() => toggleTrimestre(t)}
                    className={`px-3 py-1 rounded-full border text-sm transition ${
                      trimestresSel.has(t) ? 'bg-steel text-white border-steel' : 'bg-white text-ink/70 border-mist hover:border-steel/50'
                    }`}>
                    {t}
                  </button>
                ))}
              </div>
            )}
          </div>
          {base && colActual && base !== colActual && (
            <label className="flex items-center gap-2 text-sm text-ink/70">
              <input type="checkbox" checked={soloPendientes} onChange={(e) => setSoloPendientes(e.target.checked)} />
              Mostrar solo los <b>pendientes</b> en {colActual} (tenían en {base} y aún no en {colActual})
            </label>
          )}
        </div>
      </Card>

      <Card
        title={`Clientes (${filas.length})`}
        action={
          <Button
            variant="secondary"
            onClick={exportarClientes}
            disabled={loading || columnas.length === 0 || filas.length === 0}
          >
            Exportar clientes
          </Button>
        }
      >
        {loading ? (
          <Loading />
        ) : columnas.length === 0 ? (
          <EmptyState>No hay trimestres con asignaciones para comparar.</EmptyState>
        ) : filas.length === 0 ? (
          <EmptyState>No hay clientes con ese filtro.</EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-slate-500 border-b border-slate-200">
                  <th className="py-2 font-medium sticky left-0 bg-white">Cliente</th>
                  {columnas.map((t) => (
                    <th key={t} className="py-2 px-3 font-medium text-center whitespace-nowrap">
                      {t}{t === base ? ' (base)' : ''}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {visibles.map((c) => {
                  const conteo = porCliente.get(c.id) || {}
                  return (
                    <tr key={c.id} className="border-b border-slate-100">
                      <td className="py-2 pr-4 font-medium text-ink sticky left-0 bg-white">{c.razonSocial}</td>
                      {columnas.map((t) => {
                        const n = conteo[t] || 0
                        return (
                          <td key={t} className="py-2 px-3 text-center">
                            {n > 0 ? (
                              <span className="font-semibold text-ink">{n}</span>
                            ) : (
                              <Badge color="amber">Pendiente</Badge>
                            )}
                          </td>
                        )
                      })}
                    </tr>
                  )
                })}
              </tbody>
            </table>
            <Pagination page={page} totalPages={totalPages} onChange={setPage} />
          </div>
        )}
      </Card>
    </div>
  )
}
