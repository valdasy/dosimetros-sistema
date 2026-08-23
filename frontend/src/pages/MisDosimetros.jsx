import { useEffect, useMemo, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { getMisAsignaciones, getMisClientes, getClientes, buscarAsignaciones } from '../api/endpoints'
import { Card, Input, Button, Alert, Loading, EmptyState, Pagination } from '../components/ui'
import Combobox from '../components/Combobox'
import { useToast } from '../components/Toast'

const POR_PAGINA = 25

// Ordena trimestres 'QTYYYY' del más reciente al más antiguo.
function ordenarTrimestres(lista) {
  return [...lista].sort((a, b) => {
    const ay = a.slice(2), by = b.slice(2)
    if (ay !== by) return by.localeCompare(ay)
    return b.slice(0, 1).localeCompare(a.slice(0, 1))
  })
}

// Orden solicitado: fecha de asignación, tarea, bandeja, slot.
function comparar(a, b) {
  const fa = a.fechaAsignacion || ''
  const fb = b.fechaAsignacion || ''
  if (fa !== fb) return fa.localeCompare(fb)
  const ta = Number(a.numeroTarea) || 0
  const tb = Number(b.numeroTarea) || 0
  if (ta !== tb) return ta - tb
  const ba = a.numeroBandeja ?? -1
  const bb = b.numeroBandeja ?? -1
  if (ba !== bb) return ba - bb
  return (a.slotBandeja ?? -1) - (b.slotBandeja ?? -1)
}

// Agrupa las asignaciones por fecha de asignación (lotes).
function agruparLotes(asignaciones) {
  const map = new Map()
  for (const a of asignaciones) {
    const f = a.fechaAsignacion || 'Sin fecha'
    if (!map.has(f)) map.set(f, [])
    map.get(f).push(a)
  }
  return [...map.entries()].map(([fecha, items]) => ({ fecha, items, cantidad: items.length }))
}

export default function MisDosimetros() {
  const { rol } = useAuth()
  const esEjecutivo = rol === 'EJECUTIVO'
  const toast = useToast()

  const [clientes, setClientes] = useState([])
  const [clienteId, setClienteId] = useState('')
  const [asignaciones, setAsignaciones] = useState([])
  const [trimestresSel, setTrimestresSel] = useState(() => new Set())
  const [fecha, setFecha] = useState('')
  const [loading, setLoading] = useState(false)
  const [buscado, setBuscado] = useState(false)
  const [page, setPage] = useState(1)
  const [exportando, setExportando] = useState(false)
  const [error, setError] = useState('')
  const [grupoAbierto, setGrupoAbierto] = useState(null)

  // Carga la lista de clientes para el buscador (según el rol).
  useEffect(() => {
    const p = esEjecutivo ? getMisClientes() : getClientes()
    p.then((cs) => setClientes(cs.map((c) => ({ value: c.id, label: c.razonSocial }))))
      .catch((err) =>
        setError(
          err.response?.status === 409
            ? 'Tu usuario no tiene un ejecutivo asociado. Contacta a un administrador.'
            : 'No se pudieron cargar los clientes'
        )
      )
  }, [esEjecutivo])

  // Al elegir un cliente se cargan TODAS sus asignaciones (todos los trimestres).
  const elegirCliente = (id) => {
    setClienteId(id)
    setTrimestresSel(new Set())
    setFecha('')
    if (!id) {
      setAsignaciones([])
      setBuscado(false)
      return
    }
    setLoading(true)
    setError('')
    const p = esEjecutivo ? getMisAsignaciones({ clienteId: id }) : buscarAsignaciones({ clienteId: id })
    p.then((data) => {
      setAsignaciones([...data].sort(comparar))
      setBuscado(true)
      setPage(1)
    })
      .catch(() => setError('No se pudieron cargar las asignaciones'))
      .finally(() => setLoading(false))
  }

  // Trimestres presentes en las asignaciones del cliente (para los chips).
  const trimestresDisp = useMemo(
    () => ordenarTrimestres([...new Set(asignaciones.map((a) => a.trimestre))]),
    [asignaciones]
  )

  const toggleTrimestre = (t) => setTrimestresSel((prev) => {
    const next = new Set(prev)
    if (next.has(t)) next.delete(t); else next.add(t)
    return next
  })

  // Filtra por los trimestres seleccionados (o todos) y por fecha.
  const filtradas = useMemo(() => {
    return asignaciones.filter((a) => {
      if (trimestresSel.size > 0 && !trimestresSel.has(a.trimestre)) return false
      if (fecha && a.fechaAsignacion !== fecha) return false
      return true
    })
  }, [asignaciones, trimestresSel, fecha])

  useEffect(() => { setPage(1) }, [trimestresSel, fecha])

  const grupos = useMemo(() => agruparLotes(filtradas), [filtradas])
  const totalPages = Math.ceil(filtradas.length / POR_PAGINA)
  const visibles = filtradas.slice((page - 1) * POR_PAGINA, page * POR_PAGINA)

  // Exporta una lista de asignaciones a CSV (orden fecha/tarea/bandeja/slot).
  const exportarLista = (items, sufijo) => {
    if (!items.length) {
      toast.error('No hay datos para exportar.')
      return
    }
    const sep = ';'
    const esc = (v) => {
      const s = String(v ?? '')
      return /[";\n\r]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s
    }
    const enc = ['N° dosímetro', 'Cliente', 'Empresa', 'Trimestre', 'Fecha asignación',
      'Tipo porta', 'Tarea', 'Bandeja', 'Slot', 'Trello']
    const filas = items.map((a) => [
      a.numeroDosimetro, a.clienteNombre, a.empresaNombre, a.trimestre, a.fechaAsignacion,
      a.tipoPortaNombre, a.numeroTarea || '', a.numeroBandeja ?? '', a.slotBandeja ?? '',
      a.linkTrello || '',
    ])
    const csv = '﻿' + [enc, ...filas].map((r) => r.map(esc).join(sep)).join('\r\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `asignaciones_${sufijo}.csv`
    a.click()
    URL.revokeObjectURL(url)
    toast.success(`Exportados ${items.length} dosímetros`)
  }

  const exportarTodos = () => {
    setExportando(true)
    try {
      exportarLista(filtradas, `${new Date().toISOString().slice(0, 10)}`)
    } finally {
      setExportando(false)
    }
  }

  const toggleGrupo = (fecha) => setGrupoAbierto((prev) => (prev === fecha ? null : fecha))

  // Copia solo la columna de números de dosímetro (uno por línea, en el orden
  // tarea → bandeja → slot) para pegarla directo en Excel.
  const copiarDosimetros = async (items) => {
    const texto = items.map((a) => a.numeroDosimetro).join('\n')
    try {
      await navigator.clipboard.writeText(texto)
      toast.success(`Copiados ${items.length} dosímetros`)
    } catch {
      toast.error('No se pudo copiar automáticamente. Usa la descarga del grupo.')
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-ink">
          {esEjecutivo ? 'Mis dosímetros' : 'Asignaciones por cliente'}
        </h1>
        <p className="text-sm text-slate-500 mt-0.5">
          Elige un <b>cliente</b>, luego uno o varios <b>trimestres</b> (y/o una fecha). La
          información se muestra recién al seleccionar los trimestres, para no cargar todo de golpe.
        </p>
      </div>

      {error && <Alert type="error">{error}</Alert>}

      <Card title="Cliente">
        <div className="max-w-md">
          <Combobox
            label="Cliente"
            options={clientes}
            value={clienteId}
            onChange={elegirCliente}
            placeholder="Escribe para buscar…"
          />
        </div>
      </Card>

      {loading ? (
        <Loading />
      ) : !buscado ? (
        <Card><EmptyState>Selecciona un cliente para ver sus asignaciones.</EmptyState></Card>
      ) : asignaciones.length === 0 ? (
        <Card><EmptyState>Este cliente no tiene asignaciones.</EmptyState></Card>
      ) : (
        <>
          <Card title="Filtrar por trimestre y fecha">
            <div className="space-y-4">
              <div>
                <div className="flex items-center justify-between mb-2">
                  <p className="text-xs font-semibold uppercase tracking-wide text-ink/50">Trimestres</p>
                  <span className="text-xs text-ink/40">
                    {trimestresSel.size === 0 ? 'Todos' : `${trimestresSel.size} seleccionados`}
                  </span>
                </div>
                <div className="flex flex-wrap gap-2">
                  {trimestresDisp.map((t) => (
                    <button key={t} type="button" onClick={() => toggleTrimestre(t)}
                      className={`px-3 py-1 rounded-full border text-sm transition ${
                        trimestresSel.has(t) ? 'bg-steel text-white border-steel' : 'bg-white text-ink/70 border-mist hover:border-steel/50'
                      }`}>
                      {t}
                    </button>
                  ))}
                </div>
              </div>
              <div className="max-w-xs">
                <Input label="Fecha de asignación (opcional)" type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} />
              </div>
            </div>
          </Card>

          {(trimestresSel.size === 0 && !fecha) ? (
            <Card>
              <EmptyState>
                Selecciona uno o más <b>trimestres</b> (o una fecha) para ver y descargar la información.
              </EmptyState>
            </Card>
          ) : (<>
          <Card
            title={`Grupos de asignación por fecha (${grupos.length})`}
            action={
              <Button variant="secondary" onClick={exportarTodos} disabled={exportando || filtradas.length === 0}>
                {exportando ? 'Exportando…' : 'Descargar todos'}
              </Button>
            }
          >
            {filtradas.length === 0 ? (
              <EmptyState>No hay asignaciones con esos filtros.</EmptyState>
            ) : (
              <>
                <p className="text-sm text-slate-500 mb-3">
                  {filtradas.length} dosímetros en {grupos.length} grupo(s) por fecha. Descarga un
                  grupo puntual o todos juntos.
                </p>
                <div className="divide-y divide-mist/60">
                  {grupos.map((g) => (
                    <div key={g.fecha} className="py-2.5">
                      <div className="flex items-center justify-between gap-3">
                        <button
                          type="button"
                          onClick={() => toggleGrupo(g.fecha)}
                          className="text-sm text-left hover:underline"
                        >
                          <span className="font-medium text-ink">
                            {grupoAbierto === g.fecha ? '▾' : '▸'} {g.fecha}
                          </span>
                          <span className="text-slate-500"> · {g.cantidad} dosímetros</span>
                        </button>
                        <button
                          type="button"
                          onClick={() => exportarLista(g.items, `${g.fecha}`)}
                          className="text-sm text-steel hover:underline whitespace-nowrap"
                        >
                          Descargar grupo ↓
                        </button>
                      </div>

                      {grupoAbierto === g.fecha && (
                        <div className="mt-2 rounded-lg border border-mist/60 bg-cream/40 p-3">
                          <div className="flex justify-end mb-2">
                            <Button variant="secondary" onClick={() => copiarDosimetros(g.items)}>
                              Copiar dosímetros
                            </Button>
                          </div>
                          <div className="overflow-x-auto max-h-72 overflow-y-auto">
                            <table className="w-full text-sm">
                              <thead className="sticky top-0 bg-cream/80">
                                <tr className="text-left text-slate-500 border-b border-slate-200">
                                  <th className="py-1.5 font-medium">N° dosímetro</th>
                                  <th className="py-1.5 font-medium">Tarea</th>
                                  <th className="py-1.5 font-medium">Bandeja</th>
                                  <th className="py-1.5 font-medium">Slot</th>
                                </tr>
                              </thead>
                              <tbody>
                                {g.items.map((a) => (
                                  <tr key={a.id} className="border-b border-slate-100">
                                    <td className="py-1.5 font-medium text-ink">{a.numeroDosimetro}</td>
                                    <td className="py-1.5 text-slate-600">{a.numeroTarea || '—'}</td>
                                    <td className="py-1.5 text-slate-600">{a.numeroBandeja ?? '—'}</td>
                                    <td className="py-1.5 text-slate-600">{a.slotBandeja ?? '—'}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </>
            )}
          </Card>

          <Card title={`Dosímetros (${filtradas.length})`}>
            {filtradas.length === 0 ? (
              <EmptyState>No hay asignaciones con esos filtros.</EmptyState>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-slate-500 border-b border-slate-200">
                      <th className="py-2 font-medium">N° dosímetro</th>
                      <th className="py-2 font-medium">Cliente</th>
                      <th className="py-2 font-medium">Empresa</th>
                      <th className="py-2 font-medium">Trimestre</th>
                      <th className="py-2 font-medium">Fecha asig.</th>
                      <th className="py-2 font-medium">Porta</th>
                      <th className="py-2 font-medium">Tarea</th>
                      <th className="py-2 font-medium">Bandeja/Slot</th>
                      <th className="py-2 font-medium">Trello</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visibles.map((a) => (
                      <tr key={a.id} className="border-b border-slate-100">
                        <td className="py-2.5 font-medium text-ink">{a.numeroDosimetro}</td>
                        <td className="py-2.5 text-slate-600">{a.clienteNombre}</td>
                        <td className="py-2.5 text-slate-600">{a.empresaNombre}</td>
                        <td className="py-2.5 text-slate-600">{a.trimestre}</td>
                        <td className="py-2.5 text-slate-600">{a.fechaAsignacion}</td>
                        <td className="py-2.5 text-slate-600">{a.tipoPortaNombre}</td>
                        <td className="py-2.5 text-slate-600">{a.numeroTarea || '—'}</td>
                        <td className="py-2.5 text-slate-600">
                          {a.numeroBandeja != null ? `${a.numeroBandeja} / ${a.slotBandeja}` : '—'}
                        </td>
                        <td className="py-2.5">
                          {a.linkTrello ? (
                            <a href={a.linkTrello} target="_blank" rel="noreferrer" className="text-steel hover:underline">Ver</a>
                          ) : ('—')}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <Pagination page={page} totalPages={totalPages} onChange={setPage} />
              </div>
            )}
          </Card>
          </>)}
        </>
      )}
    </div>
  )
}
