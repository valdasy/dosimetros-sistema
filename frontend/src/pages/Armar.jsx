import { useEffect, useMemo, useState } from 'react'
import {
  getTiposPorta,
  getResumenArmadoTareas,
  getDosimetrosDeTarea,
  actualizarTipoPortaRango,
  armarSeleccion,
} from '../api/endpoints'
import { Card, Button, Input, Select, Alert, Badge, Loading, EmptyState, Modal } from '../components/ui'
import { useToast } from '../components/Toast'

// Un dosímetro está pendiente de armar si no tiene porta o si su porta es una
// "Sin armar (…)". Armado = tiene una porta real.
function esPendiente(d) {
  if (!d.tipoPortaId) return true
  return (d.tipoPortaNombre || '').toLowerCase().startsWith('sin armar')
}

// Agrupa los dosímetros de la tarea en bandejas con su estado de armado.
function agruparBandejas(dosimetros) {
  const map = new Map()
  for (const d of dosimetros) {
    const banda = d.numeroBandeja ?? 0
    if (!map.has(banda)) map.set(banda, [])
    map.get(banda).push(d)
  }
  const bandejas = [...map.entries()]
    .map(([numeroBandeja, slots]) => {
      slots.sort((a, b) => (a.slotBandeja ?? 0) - (b.slotBandeja ?? 0))
      const pendientes = slots.filter(esPendiente).length
      const armados = slots.length - pendientes
      // Solo portas reales (excluye las "Sin armar").
      const portas = [...new Set(slots.filter((s) => !esPendiente(s)).map((s) => s.tipoPortaNombre))]
      let estado = 'parcial'
      if (armados === 0) estado = 'sin-armar'
      else if (pendientes === 0) estado = 'armada'
      return { numeroBandeja, slots, armados, pendientes, total: slots.length, portas, estado }
    })
    .sort((a, b) => a.numeroBandeja - b.numeroBandeja)
  return bandejas
}

const ESTADO_BANDEJA = {
  'sin-armar': { label: 'Sin armar', color: 'amber' },
  parcial: { label: 'Parcial', color: 'blue' },
  armada: { label: 'Armada', color: 'green' },
}

export default function Armar() {
  const [portas, setPortas] = useState([])
  const [tareaId, setTareaId] = useState('')
  const [dosimetros, setDosimetros] = useState([])
  const [cargandoMapa, setCargandoMapa] = useState(false)
  const [soloPendientes, setSoloPendientes] = useState(false)

  // Resumen de armado de todas las tareas
  const [resumen, setResumen] = useState([])
  const [resumenFiltro, setResumenFiltro] = useState('todas') // todas | pendientes | armadas
  const [busqueda, setBusqueda] = useState('')

  // Modo rápido (por rango de bandejas)
  const [rango, setRango] = useState({ desde: '', hasta: '', tipoPortaId: '' })
  const [armandoRango, setArmandoRango] = useState(false)

  // Modo preciso (grilla de una bandeja)
  const [bandejaAbierta, setBandejaAbierta] = useState(null)
  const [seleccion, setSeleccion] = useState(() => new Set())
  const [portaPreciso, setPortaPreciso] = useState('')
  const [armandoPreciso, setArmandoPreciso] = useState(false)

  const [error, setError] = useState('')
  const toast = useToast()

  const cargarResumen = () => getResumenArmadoTareas().then(setResumen).catch(() => {})

  useEffect(() => {
    getTiposPorta().then(setPortas).catch(() => {})
    cargarResumen()
  }, [])

  const cargarMapa = (id) => {
    if (!id) {
      setDosimetros([])
      return
    }
    setCargandoMapa(true)
    getDosimetrosDeTarea(id)
      .then((data) => {
        setDosimetros(data)
        const bandas = data.map((d) => d.numeroBandeja).filter((n) => n != null)
        if (bandas.length) {
          setRango((r) => ({ ...r, desde: String(Math.min(...bandas)), hasta: String(Math.max(...bandas)) }))
        }
      })
      .catch(() => toast.error('No se pudo cargar el mapa de la tarea'))
      .finally(() => setCargandoMapa(false))
  }

  const onSelectTarea = (v) => {
    setTareaId(v)
    setError('')
    cargarMapa(v)
  }

  const bandejas = useMemo(() => agruparBandejas(dosimetros), [dosimetros])
  const bandejasVisibles = soloPendientes ? bandejas.filter((b) => b.pendientes > 0) : bandejas

  // Tipo de dosímetro de la tarea → portas compatibles.
  const tipoDosimetroId = dosimetros[0]?.tipoDosimetroId
  const portasDelTipo = tipoDosimetroId
    ? portas.filter((p) => String(p.tipoDosimetroId) === String(tipoDosimetroId))
    : portas
  const portasCompat = portasDelTipo.filter((p) => !(p.nombre || '').toLowerCase().startsWith('sin armar'))
  // Portas "Sin armar" de la tecnología de la tarea (permiten des-armar un rango).
  // Se muestran con su nombre real (ej. "Sin armar (Cristal)"), no una etiqueta genérica.
  const portasSinArmar = portasDelTipo.filter((p) => (p.nombre || '').toLowerCase().startsWith('sin armar'))
  const esPortaSinArmar = (id) => portasSinArmar.some((p) => String(p.id) === String(id))

  const totalPendientes = bandejas.reduce((a, b) => a + b.pendientes, 0)
  const totalArmados = bandejas.reduce((a, b) => a + b.armados, 0)

  // Resumen de tareas: conteos y lista filtrada
  const conteoResumen = useMemo(() => {
    let armadas = 0, parciales = 0, sinArmar = 0
    for (const t of resumen) {
      if (t.estado === 'armada') armadas++
      else if (t.estado === 'sin-armar') sinArmar++
      else parciales++
    }
    return { armadas, parciales, sinArmar, total: resumen.length }
  }, [resumen])

  const resumenFiltrado = useMemo(() => {
    const q = busqueda.trim().toLowerCase()
    return resumen.filter((t) => {
      if (resumenFiltro === 'armadas' && t.estado !== 'armada') return false
      if (resumenFiltro === 'pendientes' && t.estado === 'armada') return false
      if (q && !String(t.numeroTarea).toLowerCase().includes(q)) return false
      return true
    })
  }, [resumen, resumenFiltro, busqueda])

  const onArmarRango = async (e) => {
    e.preventDefault()
    setError('')
    if (!tareaId) return setError('Selecciona una tarea')
    if (!rango.tipoPortaId) return setError('Selecciona el tipo de porta')
    if (!rango.desde || !rango.hasta) return setError('Indica el rango de bandejas')
    setArmandoRango(true)
    try {
      const data = await actualizarTipoPortaRango({
        tareaId: Number(tareaId),
        bandejaDesde: Number(rango.desde),
        bandejaHasta: Number(rango.hasta),
        slotDesde: null,
        slotHasta: null,
        tipoPortaId: Number(rango.tipoPortaId),
      })
      const esSinArmar = esPortaSinArmar(rango.tipoPortaId)
      toast.success(`${data.dosimetrosActualizados} dosímetros ${esSinArmar ? 'des-armados' : 'armados'}`)
      cargarMapa(tareaId)
      cargarResumen()
    } catch (err) {
      const msg = err.response?.data?.message || 'No se pudo armar el rango'
      setError(msg)
      toast.error(msg)
    } finally {
      setArmandoRango(false)
    }
  }

  const abrirBandeja = (bandeja) => {
    setBandejaAbierta(bandeja)
    setSeleccion(new Set())
    setPortaPreciso(rango.tipoPortaId || '')
  }

  const toggleSlot = (slot) => {
    if (!esPendiente(slot)) return // ya armado: no seleccionable
    setSeleccion((prev) => {
      const next = new Set(prev)
      if (next.has(slot.id)) next.delete(slot.id)
      else next.add(slot.id)
      return next
    })
  }

  const seleccionarPendientes = () => {
    const pendientes = bandejaAbierta.slots.filter(esPendiente).map((s) => s.id)
    setSeleccion((prev) => (prev.size === pendientes.length ? new Set() : new Set(pendientes)))
  }

  const onArmarSeleccion = async () => {
    if (!portaPreciso) return setError('Selecciona el tipo de porta')
    if (seleccion.size === 0) return
    setArmandoPreciso(true)
    try {
      const data = await armarSeleccion({
        dosimetroIds: [...seleccion],
        tipoPortaId: Number(portaPreciso),
      })
      toast.success(`${data.dosimetrosActualizados} dosímetros armados`)
      setBandejaAbierta(null)
      cargarMapa(tareaId)
      cargarResumen()
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo armar la selección')
    } finally {
      setArmandoPreciso(false)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-ink">Armar dosímetros</h1>
        <p className="text-sm text-slate-500 mt-0.5">
          Asigna el tipo de porta a los dosímetros de una tarea. Usa el armado rápido
          por rango de bandejas y, para bandejas incompletas, la grilla de slots.
        </p>
      </div>

      {/* Resumen de armado de todas las tareas (clickeable) */}
      <Card
        title="Estado de armado por tarea"
        action={
          <div className="flex items-center gap-3 text-xs">
            <span className="text-emerald-600">{conteoResumen.armadas} armadas</span>
            <span className="text-steel">{conteoResumen.parciales} parciales</span>
            <span className="text-amber-600">{conteoResumen.sinArmar} sin armar</span>
          </div>
        }
      >
        <div className="flex flex-wrap items-center gap-3 mb-3">
          <div className="inline-flex rounded-lg border border-mist overflow-hidden text-sm">
            {[
              { k: 'todas', l: 'Todas' },
              { k: 'pendientes', l: 'Con pendientes' },
              { k: 'armadas', l: 'Armadas' },
            ].map((op) => (
              <button
                key={op.k}
                type="button"
                onClick={() => setResumenFiltro(op.k)}
                className={`px-3 py-1.5 ${resumenFiltro === op.k ? 'bg-steel text-white' : 'bg-white text-ink/70 hover:bg-mist/20'}`}
              >
                {op.l}
              </button>
            ))}
          </div>
          <div className="w-48">
            <Input placeholder="Buscar tarea…" value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
          </div>
        </div>

        {resumenFiltrado.length === 0 ? (
          <EmptyState>No hay tareas con ese filtro.</EmptyState>
        ) : (
          <div className="overflow-x-auto max-h-96 overflow-y-auto">
            <table className="w-full text-sm">
              <thead className="sticky top-0 bg-white">
                <tr className="text-left text-slate-500 border-b border-slate-200">
                  <th className="py-2 font-medium">Tarea</th>
                  <th className="py-2 font-medium w-40">Progreso</th>
                  <th className="py-2 font-medium text-right">Armados</th>
                  <th className="py-2 font-medium text-right">Pendientes</th>
                  <th className="py-2 font-medium">Estado</th>
                </tr>
              </thead>
              <tbody>
                {resumenFiltrado.map((t) => {
                  const est = ESTADO_BANDEJA[t.estado]
                  const pct = t.total ? Math.round((t.armados / t.total) * 100) : 0
                  const activa = String(t.tareaId) === String(tareaId)
                  return (
                    <tr
                      key={t.tareaId}
                      onClick={() => onSelectTarea(t.tareaId)}
                      className={`border-b border-slate-100 cursor-pointer ${activa ? 'bg-steel/10' : 'hover:bg-mist/10'}`}
                    >
                      <td className="py-2 font-medium text-steel">{t.numeroTarea}</td>
                      <td className="py-2">
                        <div className="flex items-center gap-2">
                          <div className="flex-1 h-2 rounded-full bg-mist/50 overflow-hidden">
                            <div className="h-full bg-emerald-400" style={{ width: `${pct}%` }} />
                          </div>
                          <span className="text-xs text-ink/50 w-9 text-right">{pct}%</span>
                        </div>
                      </td>
                      <td className="py-2 text-right text-ink/80">{t.armados}/{t.total}</td>
                      <td className="py-2 text-right">
                        {t.pendientes > 0 ? <span className="text-amber-600 font-medium">{t.pendientes}</span> : <span className="text-slate-300">0</span>}
                      </td>
                      <td className="py-2"><Badge color={est.color}>{est.label}</Badge></td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {cargandoMapa && <Loading label="Cargando mapa de la tarea…" />}

      {!cargandoMapa && tareaId && dosimetros.length === 0 && (
        <EmptyState>La tarea no tiene dosímetros cargados.</EmptyState>
      )}

      {!cargandoMapa && dosimetros.length > 0 && (
        <>
          {/* Modo rápido */}
          <Card title="Armado rápido (por rango de bandejas)">
            <form onSubmit={onArmarRango} className="space-y-4">
              <p className="text-sm text-slate-500">
                Arma varias bandejas completas de una vez. Para distintos portas, haz una
                acción por cada porta (ej. bandejas 1–10 Gringo, luego 11–15 Viejo).
              </p>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 items-end">
                <Input
                  label="Bandeja desde"
                  type="number"
                  min="1"
                  value={rango.desde}
                  onChange={(e) => setRango({ ...rango, desde: e.target.value })}
                />
                <Input
                  label="Bandeja hasta"
                  type="number"
                  min="1"
                  value={rango.hasta}
                  onChange={(e) => setRango({ ...rango, hasta: e.target.value })}
                />
                <Select
                  label="Tipo de porta"
                  value={rango.tipoPortaId}
                  onChange={(e) => setRango({ ...rango, tipoPortaId: e.target.value })}
                >
                  <option value="">Selecciona…</option>
                  {portasCompat.map((p) => (
                    <option key={p.id} value={p.id}>{p.nombre}</option>
                  ))}
                  {portasSinArmar.map((p) => (
                    <option key={p.id} value={p.id}>{p.nombre} (quitar porta)</option>
                  ))}
                </Select>
                <Button type="submit" disabled={armandoRango}>
                  {armandoRango ? 'Armando…' : 'Armar rango'}
                </Button>
              </div>
            </form>
          </Card>

          {error && <Alert type="error">{error}</Alert>}

          {/* Mapa de bandejas */}
          <Card
            title={`Bandejas de la tarea · ${totalArmados} armados / ${totalPendientes} pendientes`}
            action={
              <label className="flex items-center gap-2 text-sm text-ink/70">
                <input
                  type="checkbox"
                  checked={soloPendientes}
                  onChange={(e) => setSoloPendientes(e.target.checked)}
                />
                Solo pendientes
              </label>
            }
          >
            {bandejasVisibles.length === 0 ? (
              <EmptyState>No hay bandejas que mostrar.</EmptyState>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {bandejasVisibles.map((b) => {
                  const est = ESTADO_BANDEJA[b.estado]
                  return (
                    <div key={b.numeroBandeja} className="border border-mist/60 rounded-xl p-3">
                      <div className="flex items-center justify-between">
                        <span className="font-semibold text-ink">Bandeja {b.numeroBandeja}</span>
                        <Badge color={est.color}>{est.label}</Badge>
                      </div>
                      <p className="text-sm text-ink/70 mt-1">
                        {b.armados}/{b.total} armados
                        {b.pendientes > 0 && <span className="text-amber-600"> · {b.pendientes} pendientes</span>}
                      </p>
                      {b.portas.length > 0 && (
                        <p className="text-xs text-slate-500 mt-0.5 truncate">Portas: {b.portas.join(', ')}</p>
                      )}
                      <button
                        type="button"
                        onClick={() => abrirBandeja(b)}
                        className="mt-2 text-sm text-steel hover:underline"
                      >
                        Ver / editar slots ▸
                      </button>
                    </div>
                  )
                })}
              </div>
            )}
          </Card>
        </>
      )}

      {/* Modo preciso: grilla de slots de una bandeja */}
      {bandejaAbierta && (
        <Modal
          title={`Bandeja ${bandejaAbierta.numeroBandeja} · slots`}
          onClose={() => setBandejaAbierta(null)}
        >
          <div className="space-y-4">
            <div className="flex flex-wrap items-center gap-4 text-xs text-ink/60">
              <span className="flex items-center gap-1.5">
                <span className="w-3.5 h-3.5 rounded bg-emerald-100 border border-emerald-300 inline-block" /> pendiente (clic para seleccionar)
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-3.5 h-3.5 rounded bg-steel inline-block" /> seleccionado
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-3.5 h-3.5 rounded bg-mist/60 border border-mist inline-block" /> armado (bloqueado)
              </span>
            </div>

            <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 gap-1.5">
              {bandejaAbierta.slots.map((s) => {
                const armado = !esPendiente(s)
                const sel = seleccion.has(s.id)
                let cls = 'bg-emerald-50 border-emerald-300 text-emerald-900 hover:bg-emerald-100'
                if (armado) cls = 'bg-mist/50 border-mist text-ink/45 cursor-not-allowed'
                else if (sel) cls = 'bg-steel border-steel text-white'
                return (
                  <button
                    key={s.id}
                    type="button"
                    disabled={armado}
                    onClick={() => toggleSlot(s)}
                    title={
                      armado
                        ? `Slot ${s.slotBandeja} · dosímetro ${s.numero} · ${s.tipoPortaNombre} (armado)`
                        : `Slot ${s.slotBandeja} · dosímetro ${s.numero} (pendiente)`
                    }
                    className={`rounded border px-1.5 py-1.5 flex flex-col items-center justify-center leading-tight ${cls}`}
                  >
                    <span className={`text-[10px] ${sel ? 'text-white/80' : 'text-ink/45'}`}>Slot {s.slotBandeja}</span>
                    <span className="text-xs font-semibold">{s.numero}</span>
                  </button>
                )
              })}
            </div>

            <div className="flex flex-wrap items-center gap-3 pt-2 border-t border-mist/60">
              <button type="button" onClick={seleccionarPendientes} className="text-sm text-steel hover:underline">
                Seleccionar todos los pendientes
              </button>
              <div className="flex-1" />
              <div className="w-48">
                <Select value={portaPreciso} onChange={(e) => setPortaPreciso(e.target.value)}>
                  <option value="">Tipo de porta…</option>
                  {portasCompat.map((p) => (
                    <option key={p.id} value={p.id}>{p.nombre}</option>
                  ))}
                </Select>
              </div>
              <Button onClick={onArmarSeleccion} disabled={armandoPreciso || seleccion.size === 0}>
                {armandoPreciso ? 'Armando…' : `Armar seleccionados (${seleccion.size})`}
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
