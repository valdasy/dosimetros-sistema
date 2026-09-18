import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  getStock,
  getTiposDosimetro,
  getTiposPorta,
  getStockPortas,
  exportarStockExcel,
  actualizarStockExcel,
  descargarPlantillaDosimetros,
  marcarDanado,
  marcarBueno,
  liberarDosimetro,
} from '../api/endpoints'
import { Card, Select, Badge, Button, Loading, EmptyState, Alert, Modal } from '../components/ui'
import LiberacionMasiva from '../components/LiberacionMasiva'
import { useToast } from '../components/Toast'

const estadoColor = { disponible: 'green', asignado: 'blue', baja: 'red', dañado: 'amber' }

// Construye el árbol jerárquico Tipo → Porta → Tarea → Bandeja → Slot.
function construirArbol(dosimetros) {
  const tipos = new Map()
  for (const d of dosimetros) {
    const tKey = d.tipoDosimetroNombre || 'Sin tipo'
    if (!tipos.has(tKey)) tipos.set(tKey, { nombre: tKey, total: 0, portas: new Map() })
    const tipo = tipos.get(tKey)
    tipo.total++
    const pKey = d.tipoPortaNombre || 'Sin porta'
    if (!tipo.portas.has(pKey)) tipo.portas.set(pKey, { nombre: pKey, total: 0, tareas: new Map() })
    const porta = tipo.portas.get(pKey)
    porta.total++
    const taKey = d.numeroTarea || 'Sin tarea'
    if (!porta.tareas.has(taKey)) porta.tareas.set(taKey, { nombre: taKey, total: 0, bandejas: new Map() })
    const tarea = porta.tareas.get(taKey)
    tarea.total++
    const bKey = d.numeroBandeja != null ? d.numeroBandeja : 'Sin bandeja'
    if (!tarea.bandejas.has(bKey)) tarea.bandejas.set(bKey, { nombre: bKey, total: 0, slots: [] })
    const bandeja = tarea.bandejas.get(bKey)
    bandeja.total++
    bandeja.slots.push(d)
  }
  const txt = (a, b) => String(a).localeCompare(String(b), undefined, { numeric: true })
  return [...tipos.values()]
    .sort((a, b) => txt(a.nombre, b.nombre))
    .map((tipo) => ({
      ...tipo,
      portas: [...tipo.portas.values()]
        .sort((a, b) => txt(a.nombre, b.nombre))
        .map((porta) => ({
          ...porta,
          tareas: [...porta.tareas.values()]
            .sort((a, b) => txt(a.nombre, b.nombre))
            .map((tarea) => ({
              ...tarea,
              bandejas: [...tarea.bandejas.values()]
                .sort((a, b) => (Number(a.nombre) || 0) - (Number(b.nombre) || 0))
                .map((bandeja) => ({
                  ...bandeja,
                  slots: bandeja.slots.sort((a, b) => (a.slotBandeja ?? 0) - (b.slotBandeja ?? 0)),
                })),
            })),
        })),
    }))
}

// Encabezado colapsable de un nivel del árbol.
function Fila({ nivel, abierto, onClick, label, count }) {
  return (
    <button
      type="button"
      onClick={onClick}
      style={{ paddingLeft: nivel * 18 + 12 }}
      className="w-full flex items-center justify-between py-2 pr-3 text-left border-b border-slate-100 hover:bg-mist/10"
    >
      <span className="flex items-center gap-2">
        <span className="text-ink/40 w-3">{abierto ? '▾' : '▸'}</span>
        <span className={nivel === 0 ? 'font-semibold text-ink' : 'text-ink/80'}>{label}</span>
      </span>
      <Badge color="slate">{count}</Badge>
    </button>
  )
}

export default function Stock() {
  const [tipos, setTipos] = useState([])
  const [portas, setPortas] = useState([])
  const [filtros, setFiltros] = useState({ tipoDosimetroId: '', tipoPortaId: '', estado: 'disponible' })
  const [dosimetros, setDosimetros] = useState([])
  const [detallePortas, setDetallePortas] = useState([])
  const [loading, setLoading] = useState(true)
  const [expandidos, setExpandidos] = useState(() => new Set())
  // Tarjeta de porta clicable → tareas de esa porta
  const [portaSel, setPortaSel] = useState(null) // { tipoPortaId, portaNombre }
  const [portaDosimetros, setPortaDosimetros] = useState([])
  const [portaLoading, setPortaLoading] = useState(false)
  const [tareasAbiertas, setTareasAbiertas] = useState(() => new Set())
  // Actualización de stock por archivo (upsert)
  const [archivo, setArchivo] = useState(null)
  const [subiendo, setSubiendo] = useState(false)
  const [resultado, setResultado] = useState(null)
  const [errorArchivo, setErrorArchivo] = useState('')
  const toast = useToast()

  useEffect(() => {
    getTiposDosimetro().then(setTipos).catch(() => {})
    getTiposPorta().then(setPortas).catch(() => {})
    getStockPortas().then(setDetallePortas).catch(() => {})
  }, [])

  const construirParams = useCallback(() => {
    const params = {}
    if (filtros.tipoDosimetroId) params.tipoDosimetroId = filtros.tipoDosimetroId
    if (filtros.tipoPortaId) params.tipoPortaId = filtros.tipoPortaId
    if (filtros.estado) params.estado = filtros.estado
    return params
  }, [filtros])

  const cargar = useCallback(() => {
    setLoading(true)
    getStock(construirParams())
      .then(setDosimetros)
      .catch(() => toast.error('No se pudo cargar el stock'))
      .finally(() => setLoading(false))
  }, [construirParams, toast])

  useEffect(() => {
    cargar()
  }, [cargar])

  const recargarDetalle = () => getStockPortas().then(setDetallePortas).catch(() => {})

  const bajar = (getter, nombre) => async () => {
    try {
      const blob = await getter()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = nombre
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      toast.error('No se pudo descargar el archivo')
    }
  }
  const onExportar = bajar(() => exportarStockExcel(construirParams()), 'stock.xlsx')
  const descargarPlantilla = bajar(descargarPlantillaDosimetros, 'plantilla_carga_dosimetros.xlsx')

  const onActualizarArchivo = async (e) => {
    e.preventDefault()
    setErrorArchivo('')
    setResultado(null)
    if (!archivo) {
      setErrorArchivo('Selecciona un archivo .xlsx')
      return
    }
    setSubiendo(true)
    try {
      const data = await actualizarStockExcel(archivo)
      setResultado(data)
      if (data.aplicado === false) {
        // El archivo tenía errores: no se guardó nada. Se muestra el detalle abajo.
        toast.error('El archivo tiene errores. No se guardó ningún cambio; corrígelos y vuelve a subirlo.')
      } else {
        const extraDup = data.duplicados ? `, ${data.duplicados} duplicados` : ''
        toast.success(
          `Actualización: ${data.creados} creados, ${data.actualizados} actualizados, ${data.sinCambios} sin cambios${extraDup}`
        )
        if (data.duplicados) {
          toast.error(`${data.duplicados} posible(s) duplicado(s). Revísalos en el módulo Duplicados.`)
        }
        cargar()
        recargarDetalle()
      }
    } catch (err) {
      const msg = err.response?.data?.message || 'No se pudo actualizar el stock'
      setErrorArchivo(msg)
      toast.error(msg)
    } finally {
      setSubiendo(false)
    }
  }

  const conAccion = (fn, ok) => async (id) => {
    try {
      await fn(id)
      toast.success(ok)
      cargar()
      recargarDetalle()
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo completar la acción')
    }
  }
  const onMarcarDanado = conAccion(marcarDanado, 'Dosímetro marcado como dañado')
  const onLiberar = conAccion(liberarDosimetro, 'Dosímetro liberado (vuelve a disponible)')
  const onMarcarBueno = conAccion(marcarBueno, 'Dosímetro marcado como bueno (disponible)')

  // Abre el modal con las tareas disponibles de una porta.
  const abrirPorta = (p) => {
    setPortaSel({ tipoPortaId: p.tipoPortaId, portaNombre: p.portaNombre })
    setTareasAbiertas(new Set())
    setPortaLoading(true)
    getStock({ tipoPortaId: p.tipoPortaId, estado: 'disponible' })
      .then(setPortaDosimetros)
      .catch(() => toast.error('No se pudieron cargar las tareas de la porta'))
      .finally(() => setPortaLoading(false))
  }

  const toggle = (setter) => (key) =>
    setter((prev) => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  const toggleNodo = toggle(setExpandidos)
  const toggleTarea = toggle(setTareasAbiertas)

  const portasFiltradas = filtros.tipoDosimetroId
    ? portas.filter((p) => String(p.tipoDosimetroId) === String(filtros.tipoDosimetroId))
    : portas

  const totalDisponibles = detallePortas.reduce((acc, p) => acc + (p.cantidad || 0), 0)
  const arbol = useMemo(() => construirArbol(dosimetros), [dosimetros])

  // Tareas de la porta seleccionada (agrupadas desde los dosímetros ya traídos).
  const tareasPorta = useMemo(() => {
    const map = new Map()
    for (const d of portaDosimetros) {
      const k = d.numeroTarea || 'Sin tarea'
      if (!map.has(k)) map.set(k, { numeroTarea: k, dosimetros: [] })
      map.get(k).dosimetros.push(d)
    }
    return [...map.values()].sort((a, b) =>
      String(a.numeroTarea).localeCompare(String(b.numeroTarea), undefined, { numeric: true })
    )
  }, [portaDosimetros])

  const accionesSlot = (d) => (
    <>
      {d.estado === 'asignado' && (
        <button onClick={() => onLiberar(d.id)} className="text-steel hover:underline text-sm">Liberar</button>
      )}
      {d.estado === 'disponible' && (
        <button onClick={() => onMarcarDanado(d.id)} className="text-amber-600 hover:underline text-sm">Marcar dañado</button>
      )}
      {d.estado === 'dañado' && (
        <button onClick={() => onMarcarBueno(d.id)} className="text-steel hover:underline text-sm">Marcar bueno</button>
      )}
    </>
  )

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-ink">Stock de dosímetros</h1>

      {/* Stock por porta: TODAS las portas (incluidas las que están en 0), clicables */}
      <Card title={`Stock por porta · ${totalDisponibles} disponibles en total`}>
        <p className="text-sm text-slate-500 mb-3">Toca una porta para ver sus tareas disponibles.</p>
        {detallePortas.length === 0 ? (
          <EmptyState>No hay tipos de porta registrados</EmptyState>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
            {detallePortas.map((p) => {
              const enCero = !p.cantidad
              return (
                <button
                  key={p.tipoPortaId}
                  type="button"
                  onClick={() => abrirPorta(p)}
                  disabled={enCero}
                  className={`text-left border rounded-xl p-3 transition ${
                    enCero
                      ? 'border-mist/40 bg-mist/10 cursor-default'
                      : 'border-mist/60 hover:border-steel hover:shadow-sm'
                  }`}
                >
                  <p className={`text-2xl font-bold ${enCero ? 'text-ink/30' : 'text-ink'}`}>{p.cantidad}</p>
                  <p className="text-sm font-medium text-ink/80">{p.portaNombre}</p>
                  <p className="text-xs text-slate-500">{p.tipoDosimetroNombre}</p>
                </button>
              )
            })}
          </div>
        )}
      </Card>

      {/* Actualización de stock por archivo (upsert por número) */}
      <Card title="Actualizar stock por archivo">
        <form onSubmit={onActualizarArchivo} className="space-y-3">
          <p className="text-sm text-slate-500">
            Sube un <b>.xlsx</b> (columnas: numero_dosimetro, tipo_dosimetro, tipo_porta,
            numero_tarea, numero_bandeja, slot_bandeja). Se busca por <b>número</b>: si no
            existe se crea, si cambió se actualiza y si es idéntico se deja igual. Los
            dosímetros asignados o dados de baja no se modifican.
          </p>
          <div>
            <Button type="button" variant="secondary" onClick={descargarPlantilla}>
              Descargar plantilla
            </Button>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <input
              type="file"
              accept=".xlsx"
              onChange={(e) => setArchivo(e.target.files[0])}
              className="block text-sm text-slate-600 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:bg-steel file:text-white hover:file:bg-steel/90"
            />
            <Button type="submit" disabled={subiendo}>
              {subiendo ? 'Actualizando…' : 'Actualizar stock'}
            </Button>
          </div>
        </form>
        {errorArchivo && <div className="mt-3"><Alert type="error">{errorArchivo}</Alert></div>}
        {resultado && (
          <div className="mt-4">
            {resultado.aplicado === false && (
              <div className="mb-3">
                <Alert type="error">
                  El archivo contiene datos erróneos, por lo que <b>no se guardó ningún cambio</b>.
                  Corrige los errores del listado y vuelve a subir el archivo.
                </Alert>
              </div>
            )}
            <div className="flex flex-wrap gap-4 text-sm mb-3">
              <span>Total filas: <b>{resultado.totalFilas}</b></span>
              <span className="text-emerald-600">Creados: <b>{resultado.creados}</b></span>
              <span className="text-steel">Actualizados: <b>{resultado.actualizados}</b></span>
              <span className="text-ink/60">Sin cambios: <b>{resultado.sinCambios}</b></span>
              {resultado.duplicados > 0 && (
                <span className="text-amber-600">Duplicados: <b>{resultado.duplicados}</b></span>
              )}
              <span className="text-red-600">Con problemas: <b>{resultado.fallidas}</b></span>
            </div>
            {resultado.alertas?.length > 0 && (
              <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 mb-3 max-h-60 overflow-auto">
                <p className="text-sm font-medium text-amber-800 mb-1">
                  Posibles duplicados (se cargaron; resuélvelos en el módulo Duplicados):
                </p>
                <ul className="text-sm text-amber-700 space-y-1">
                  {resultado.alertas.map((msg, i) => (
                    <li key={i}>{msg}</li>
                  ))}
                </ul>
              </div>
            )}
            {resultado.errores?.length > 0 && (
              <div className="bg-red-50 border border-red-200 rounded-lg p-3 max-h-60 overflow-auto">
                <ul className="text-sm text-red-700 space-y-1">
                  {resultado.errores.map((msg, i) => (
                    <li key={i}>{msg}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </Card>

      {/* Corrección de asignaciones (liberar) — solo Administrador */}
      <LiberacionMasiva />

      {/* Filtros */}
      <Card title="Filtros">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Select
            label="Tipo de dosímetro"
            value={filtros.tipoDosimetroId}
            onChange={(e) => setFiltros({ ...filtros, tipoDosimetroId: e.target.value, tipoPortaId: '' })}
          >
            <option value="">Todos</option>
            {tipos.map((t) => (
              <option key={t.id} value={t.id}>{t.nombre}</option>
            ))}
          </Select>
          <Select
            label="Estado de armado (porta)"
            value={filtros.tipoPortaId}
            onChange={(e) => setFiltros({ ...filtros, tipoPortaId: e.target.value })}
          >
            <option value="">Todos</option>
            {portasFiltradas.map((p) => (
              <option key={p.id} value={p.id}>{p.nombre}</option>
            ))}
          </Select>
          <Select
            label="Estado"
            value={filtros.estado}
            onChange={(e) => setFiltros({ ...filtros, estado: e.target.value })}
          >
            <option value="disponible">Disponible</option>
            <option value="asignado">Asignado</option>
            <option value="dañado">Dañado</option>
            <option value="baja">Baja</option>
            <option value="">Todos</option>
          </Select>
        </div>
      </Card>

      {/* Lista detallada jerárquica: Tipo → Porta → Tarea → Bandeja → Slot */}
      <Card
        title={`Lista detallada · ${dosimetros.length} dosímetros`}
        action={
          <Button variant="secondary" onClick={onExportar} disabled={dosimetros.length === 0}>
            Exportar a Excel
          </Button>
        }
      >
        {loading ? (
          <Loading />
        ) : arbol.length === 0 ? (
          <EmptyState>No hay dosímetros con esos filtros</EmptyState>
        ) : (
          <div className="border-t border-slate-100">
            {arbol.map((tipo) => {
              const kTipo = tipo.nombre
              return (
                <div key={kTipo}>
                  <Fila nivel={0} abierto={expandidos.has(kTipo)} onClick={() => toggleNodo(kTipo)}
                    label={`Tipo: ${tipo.nombre}`} count={tipo.total} />
                  {expandidos.has(kTipo) && tipo.portas.map((porta) => {
                    const kPorta = `${kTipo}|${porta.nombre}`
                    return (
                      <div key={kPorta}>
                        <Fila nivel={1} abierto={expandidos.has(kPorta)} onClick={() => toggleNodo(kPorta)}
                          label={`Porta: ${porta.nombre}`} count={porta.total} />
                        {expandidos.has(kPorta) && porta.tareas.map((tarea) => {
                          const kTarea = `${kPorta}|${tarea.nombre}`
                          return (
                            <div key={kTarea}>
                              <Fila nivel={2} abierto={expandidos.has(kTarea)} onClick={() => toggleNodo(kTarea)}
                                label={`Tarea ${tarea.nombre}`} count={tarea.total} />
                              {expandidos.has(kTarea) && tarea.bandejas.map((bandeja) => {
                                const kBandeja = `${kTarea}|${bandeja.nombre}`
                                return (
                                  <div key={kBandeja}>
                                    <Fila nivel={3} abierto={expandidos.has(kBandeja)} onClick={() => toggleNodo(kBandeja)}
                                      label={`Bandeja ${bandeja.nombre}`} count={bandeja.total} />
                                    {expandidos.has(kBandeja) && (
                                      <div className="bg-cream/30">
                                        {bandeja.slots.map((d) => (
                                          <div key={d.id}
                                            className="flex items-center justify-between text-sm py-1.5 pr-3 border-b border-slate-50"
                                            style={{ paddingLeft: 4 * 18 + 12 }}>
                                            <span className="text-ink/80">
                                              Slot <b>{d.slotBandeja ?? '—'}</b> · dosímetro <b>{d.numero}</b>
                                            </span>
                                            <span className="flex items-center gap-3">
                                              <Badge color={estadoColor[d.estado] || 'slate'}>{d.estado}</Badge>
                                              {accionesSlot(d)}
                                            </span>
                                          </div>
                                        ))}
                                      </div>
                                    )}
                                  </div>
                                )
                              })}
                            </div>
                          )
                        })}
                      </div>
                    )
                  })}
                </div>
              )
            })}
          </div>
        )}
      </Card>

      {/* Modal: tareas de la porta seleccionada */}
      {portaSel && (
        <Modal title={`Tareas disponibles · ${portaSel.portaNombre}`} onClose={() => setPortaSel(null)}>
          {portaLoading ? (
            <Loading />
          ) : tareasPorta.length === 0 ? (
            <EmptyState>Esta porta no tiene dosímetros disponibles.</EmptyState>
          ) : (
            <div className="border-t border-slate-100">
              <p className="text-sm text-ink/60 py-2">{portaDosimetros.length} dosímetros disponibles en {tareasPorta.length} tareas</p>
              {tareasPorta.map((t) => {
                const abierta = tareasAbiertas.has(t.numeroTarea)
                return (
                  <div key={t.numeroTarea}>
                    <button type="button" onClick={() => toggleTarea(t.numeroTarea)}
                      className="w-full flex items-center justify-between py-2 px-1 text-left border-b border-slate-100 hover:bg-mist/10">
                      <span className="flex items-center gap-2">
                        <span className="text-ink/40 w-3">{abierta ? '▾' : '▸'}</span>
                        <span className="text-ink/90">Tarea {t.numeroTarea}</span>
                      </span>
                      <Badge color="slate">{t.dosimetros.length}</Badge>
                    </button>
                    {abierta && (
                      <div className="pl-6 pb-2">
                        {t.dosimetros.map((d) => (
                          <div key={d.id} className="text-sm text-ink/70 py-1 border-b border-slate-50">
                            Dosímetro <b>{d.numero}</b>
                            {d.numeroBandeja != null && <span> · Bandeja {d.numeroBandeja} / Slot {d.slotBandeja}</span>}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </Modal>
      )}
    </div>
  )
}
