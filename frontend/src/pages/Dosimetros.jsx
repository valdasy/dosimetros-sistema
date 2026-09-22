import { useEffect, useState } from 'react'
import {
  getDetalleDosimetros,
  editarEspecificaciones,
  getTiposDosimetro,
  getTiposPorta,
  getClientes,
  getEjecutivos,
  getEmpresas,
  marcarDanado,
  marcarBueno,
  marcarExtraviado,
  liberarDosimetro,
  darDeBaja,
  marcarStockEmergencia,
  buscarAsignaciones,
  correccionMasivaAsignaciones,
  editarAsignacion,
  exportarCorreccionExcel,
  importarCorreccionExcel,
} from '../api/endpoints'
import { Card, Button, Input, Select, Badge, Alert, Loading, EmptyState, Modal, Pagination } from '../components/ui'
import Combobox from '../components/Combobox'
import { useToast } from '../components/Toast'
import { useNavigate } from 'react-router-dom'

const estadoColor = { disponible: 'green', asignado: 'blue', baja: 'red', dañado: 'amber', extraviado: 'slate' }

export default function Dosimetros() {
  const [vista, setVista] = useState('edicion') // 'edicion' | 'correcciones'
  const [tiposDosimetro, setTiposDosimetro] = useState([])
  const [portas, setPortas] = useState([])
  const [clientes, setClientes] = useState([])
  const [ejecutivos, setEjecutivos] = useState([])
  const [empresas, setEmpresas] = useState([])
  const toast = useToast()

  useEffect(() => {
    getTiposDosimetro().then(setTiposDosimetro).catch(() => {})
    getTiposPorta().then(setPortas).catch(() => {})
    getClientes().then(setClientes).catch(() => {})
    getEjecutivos().then(setEjecutivos).catch(() => {})
    getEmpresas().then(setEmpresas).catch(() => {})
  }, [])

  const tabBtn = (k, label) => (
    <button
      type="button"
      onClick={() => setVista(k)}
      className={`px-4 py-1.5 text-sm ${vista === k ? 'bg-steel text-white' : 'bg-white text-ink/70 hover:bg-mist/20'}`}
    >
      {label}
    </button>
  )

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-ink">Dosímetros</h1>

      <div className="inline-flex rounded-lg border border-mist overflow-hidden">
        {tabBtn('edicion', 'Edición individual')}
        {tabBtn('correcciones', 'Correcciones masivas')}
      </div>

      {vista === 'edicion' && (
        <Edicion
          tiposDosimetro={tiposDosimetro}
          portas={portas}
          clientes={clientes}
          ejecutivos={ejecutivos}
          empresas={empresas}
          toast={toast}
        />
      )}
      {vista === 'correcciones' && (
        <Correcciones
          clientes={clientes}
          ejecutivos={ejecutivos}
          empresas={empresas}
          portas={portas}
          toast={toast}
        />
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Vista 1: Edición individual (buscar por número, editar specs, cambiar estado)
// ---------------------------------------------------------------------------
function Edicion({ tiposDosimetro, portas, clientes, ejecutivos, empresas, toast }) {
  const navigate = useNavigate()
  const [numero, setNumero] = useState('')
  const [resultados, setResultados] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [editando, setEditando] = useState(null) // dosímetro en edición (especificaciones)
  const [editandoAsig, setEditandoAsig] = useState(null) // asignación del historial en edición

  const buscar = async (e) => {
    e?.preventDefault()
    setError('')
    setResultados(null)
    setLoading(true)
    try {
      const data = await getDetalleDosimetros(numero)
      setResultados(data)
    } catch (err) {
      setError(err.response?.status === 404 ? `No se encontraron dosímetros con el número ${numero}` : 'Error al buscar')
    } finally {
      setLoading(false)
    }
  }

  const recargar = () => { if (numero) buscar() }

  const accion = (fn, ok) => async (id, ...args) => {
    try {
      await fn(id, ...args)
      toast.success(ok)
      recargar()
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo completar la acción')
    }
  }
  const onDanado = accion(marcarDanado, 'Dosímetro marcado como dañado')
  const onBueno = accion(marcarBueno, 'Dosímetro marcado como bueno')
  const onLiberar = accion(liberarDosimetro, 'Dosímetro liberado (se quitó su asignación vigente)')
  const onBaja = (id) => {
    const obs = window.prompt('Motivo de la baja (opcional):', '') ?? ''
    accion(darDeBaja, 'Dosímetro dado de baja')(id, obs)
  }
  const onExtraviado = (id) => {
    const obs = window.prompt('Nota del extravío (opcional):', 'No encontrado') ?? ''
    accion(marcarExtraviado, 'Dosímetro marcado como extraviado')(id, obs)
  }
  const onDuplicado = (id) => {
    const obs = window.prompt('Observación del duplicado / stock de emergencia:', 'Duplicado') ?? 'Duplicado'
    accion(marcarStockEmergencia, 'Marcado como duplicado / stock de emergencia')(id, obs)
  }

  return (
    <div className="space-y-6">
      <Card>
        <form onSubmit={buscar} className="flex flex-col sm:flex-row gap-3 sm:items-center">
          <div className="flex-1">
            <Input
              type="number"
              value={numero}
              onChange={(e) => setNumero(e.target.value)}
              placeholder="Número físico del dosímetro…"
              required
            />
          </div>
          <Button type="submit">Buscar</Button>
        </form>
      </Card>

      {loading && <Loading label="Buscando…" />}
      {error && <Alert type="error">{error}</Alert>}

      {resultados?.map((d) => (
        <Card key={d.id}>
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-xs text-ink/50">Dosímetro · id interno {d.id}</p>
              <h2 className="text-2xl font-bold text-ink leading-tight">{d.numero}</h2>
              <div className="flex items-center gap-2 mt-1">
                <Badge color={estadoColor[d.estado] || 'slate'}>{d.estado}</Badge>
                {d.observacion && <Badge color="amber">{d.observacion}</Badge>}
              </div>
            </div>
            <div className="flex flex-col gap-2">
              <Button variant="secondary" onClick={() => setEditando(d)}>Editar especificaciones</Button>
              {d.estado === 'disponible' && (
                <Button variant="secondary" onClick={() => navigate(`/asignar?vista=individual&numero=${d.numero}`)}>
                  Asignar (individual)
                </Button>
              )}
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4">
            <Info etiqueta="Tipo" valor={d.tipoDosimetroNombre} />
            <Info etiqueta="Porta" valor={d.tipoPortaNombre} />
            <Info etiqueta="Tarea (no editable)" valor={d.numeroTarea} />
            <Info etiqueta="Bandeja/Slot (no editable)" valor={d.numeroBandeja != null ? `${d.numeroBandeja} / ${d.slotBandeja}` : '—'} />
          </div>

          {/* Acciones de estado */}
          <div className="flex flex-wrap gap-2 mt-4 pt-4 border-t border-mist/60">
            {d.estado === 'disponible' && (
              <>
                <BotonAccion onClick={() => onDanado(d.id)} color="amber">Marcar dañado</BotonAccion>
                <BotonAccion onClick={() => onDuplicado(d.id)} color="blue">Marcar duplicado</BotonAccion>
                <BotonAccion onClick={() => onExtraviado(d.id)} color="slate">Marcar extraviado</BotonAccion>
                <BotonAccion onClick={() => onBaja(d.id)} color="red">Dar de baja</BotonAccion>
              </>
            )}
            {d.estado === 'asignado' && (
              <>
                <BotonAccion onClick={() => onLiberar(d.id)} color="steel">Liberar</BotonAccion>
                <BotonAccion onClick={() => onExtraviado(d.id)} color="slate">Marcar extraviado</BotonAccion>
                <BotonAccion onClick={() => onBaja(d.id)} color="red">Dar de baja</BotonAccion>
              </>
            )}
            {d.estado === 'extraviado' && (
              <p className="text-sm text-slate-500">
                Extraviado. Se recupera automáticamente si reaparece en una carga de "Actualizar stock".
              </p>
            )}
            {d.estado === 'dañado' && (
              <>
                <BotonAccion onClick={() => onBueno(d.id)} color="steel">Marcar bueno</BotonAccion>
                <BotonAccion onClick={() => onBaja(d.id)} color="red">Dar de baja</BotonAccion>
              </>
            )}
          </div>

          {/* Historial */}
          <div className="mt-4">
            <h3 className="text-sm font-semibold text-ink mb-3">Historial de asignaciones · {d.asignaciones.length}</h3>
            {d.asignaciones.length === 0 ? (
              <p className="text-sm text-slate-400">Sin asignaciones registradas</p>
            ) : (
              <ol className="relative border-l-2 border-mist/70 ml-2 space-y-4">
                {d.asignaciones.map((a) => (
                  <li key={a.id} className="ml-5">
                    <span className="absolute -left-[7px] w-3 h-3 rounded-full bg-steel border-2 border-white" />
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge color="blue">{a.trimestre}</Badge>
                      <span className="text-xs text-ink/50">{a.fechaAsignacion}</span>
                      <button
                        type="button"
                        onClick={() => setEditandoAsig(a)}
                        className="text-xs text-steel hover:underline ml-auto"
                      >
                        Editar
                      </button>
                    </div>
                    <p className="text-sm font-semibold text-ink mt-1">{a.clienteNombre}</p>
                    <p className="text-xs text-ink/60">{a.ejecutivoNombre} · {a.empresaNombre} · {a.tipoPortaNombre}</p>
                    {a.linkTrello && (
                      <a href={a.linkTrello} target="_blank" rel="noreferrer" className="text-xs text-steel hover:underline">Ver Trello ↗</a>
                    )}
                  </li>
                ))}
              </ol>
            )}
          </div>
        </Card>
      ))}

      {editando && (
        <ModalEditar
          dosimetro={editando}
          tiposDosimetro={tiposDosimetro}
          portas={portas}
          onClose={() => setEditando(null)}
          onGuardado={() => { setEditando(null); recargar() }}
          toast={toast}
        />
      )}

      {editandoAsig && (
        <ModalEditarAsignacion
          asignacion={editandoAsig}
          clientes={clientes}
          ejecutivos={ejecutivos}
          empresas={empresas}
          portas={portas}
          onClose={() => setEditandoAsig(null)}
          onGuardado={() => { setEditandoAsig(null); recargar() }}
          toast={toast}
        />
      )}
    </div>
  )
}

// Modal para editar una asignación del historial (incluido el cliente).
function ModalEditarAsignacion({ asignacion, clientes, ejecutivos, empresas, portas, onClose, onGuardado, toast }) {
  const [form, setForm] = useState({
    clienteId: asignacion.clienteId ?? '',
    ejecutivoId: asignacion.ejecutivoId ?? '',
    empresaId: asignacion.empresaId ?? '',
    tipoPortaId: asignacion.tipoPortaId ?? '',
    trimestre: asignacion.trimestre ?? '',
    linkTrello: asignacion.linkTrello ?? '',
  })
  const [guardando, setGuardando] = useState(false)
  const set = (campo) => (v) => setForm((f) => ({ ...f, [campo]: v }))

  const guardar = async () => {
    if (!form.clienteId || !form.ejecutivoId || !form.empresaId || !form.tipoPortaId) {
      return toast.error('Completa cliente, ejecutivo, empresa y porta')
    }
    if (!form.trimestre.trim() || !form.linkTrello.trim()) {
      return toast.error('Trimestre y link de Trello son obligatorios')
    }
    setGuardando(true)
    try {
      await editarAsignacion(asignacion.id, {
        clienteId: Number(form.clienteId),
        ejecutivoId: Number(form.ejecutivoId),
        empresaId: Number(form.empresaId),
        tipoPortaId: Number(form.tipoPortaId),
        trimestre: form.trimestre.trim().toUpperCase(),
        linkTrello: form.linkTrello.trim(),
      })
      toast.success('Asignación actualizada')
      onGuardado()
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo guardar la asignación')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal title={`Editar asignación · dosímetro ${asignacion.numeroDosimetro}`} onClose={onClose}>
      <div className="space-y-4">
        <p className="text-xs text-ink/50">La tarea, bandeja y slot no se editan (vienen del dosímetro).</p>
        <Combobox label="Cliente" options={clientes.map((c) => ({ value: c.id, label: c.razonSocial }))}
          value={form.clienteId} onChange={set('clienteId')} />
        <Combobox label="Ejecutivo" options={ejecutivos.map((e) => ({ value: e.id, label: e.nombre }))}
          value={form.ejecutivoId} onChange={set('ejecutivoId')} />
        <Combobox label="Empresa" options={empresas.map((e) => ({ value: e.id, label: e.nombre }))}
          value={form.empresaId} onChange={set('empresaId')} />
        <Combobox label="Tipo de porta" options={portas.map((p) => ({ value: p.id, label: p.nombre }))}
          value={form.tipoPortaId} onChange={set('tipoPortaId')} />
        <Input label="Trimestre" value={form.trimestre} onChange={(e) => set('trimestre')(e.target.value)} placeholder="Ej: 2T2026" />
        <Input label="Link de Trello" value={form.linkTrello} onChange={(e) => set('linkTrello')(e.target.value)} placeholder="https://trello.com/c/…" />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancelar</Button>
          <Button onClick={guardar} disabled={guardando}>{guardando ? 'Guardando…' : 'Guardar'}</Button>
        </div>
      </div>
    </Modal>
  )
}

function ModalEditar({ dosimetro, tiposDosimetro, portas, onClose, onGuardado, toast }) {
  const [form, setForm] = useState({
    numero: dosimetro.numero,
    tipoDosimetroId: dosimetro.tipoDosimetroId,
    tipoPortaId: dosimetro.tipoPortaId ?? '',
    observacion: dosimetro.observacion ?? '',
  })
  const [guardando, setGuardando] = useState(false)

  const portasCompat = portas.filter((p) => String(p.tipoDosimetroId) === String(form.tipoDosimetroId))

  const guardar = async () => {
    setGuardando(true)
    try {
      await editarEspecificaciones(dosimetro.id, {
        numero: Number(form.numero),
        tipoDosimetroId: Number(form.tipoDosimetroId),
        tipoPortaId: form.tipoPortaId ? Number(form.tipoPortaId) : null,
        observacion: form.observacion || null,
      })
      toast.success('Especificaciones actualizadas')
      onGuardado()
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo guardar')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal title={`Editar dosímetro ${dosimetro.numero}`} onClose={onClose}>
      <div className="space-y-4">
        <p className="text-xs text-ink/50">La tarea, bandeja y slot no se editan aquí (se gestionan en Armar/Asignar).</p>
        <Input label="Número" type="number" value={form.numero} onChange={(e) => setForm({ ...form, numero: e.target.value })} />
        <Select
          label="Tipo de dosímetro"
          value={form.tipoDosimetroId}
          onChange={(e) => setForm({ ...form, tipoDosimetroId: e.target.value, tipoPortaId: '' })}
        >
          {tiposDosimetro.map((t) => <option key={t.id} value={t.id}>{t.nombre}</option>)}
        </Select>
        <Select label="Tipo de porta" value={form.tipoPortaId} onChange={(e) => setForm({ ...form, tipoPortaId: e.target.value })}>
          <option value="">Sin porta</option>
          {portasCompat.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
        </Select>
        <Input label="Observación" value={form.observacion} onChange={(e) => setForm({ ...form, observacion: e.target.value })} />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancelar</Button>
          <Button onClick={guardar} disabled={guardando}>{guardando ? 'Guardando…' : 'Guardar'}</Button>
        </div>
      </div>
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// Vista 2: Correcciones masivas de asignaciones
// ---------------------------------------------------------------------------
const POR_PAGINA_CORR = 25

const CAMPOS = [
  { value: 'linkTrello', label: 'Link de Trello' },
  { value: 'clienteId', label: 'Cliente' },
  { value: 'ejecutivoId', label: 'Ejecutivo' },
  { value: 'empresaId', label: 'Empresa' },
  { value: 'tipoPortaId', label: 'Tipo de porta' },
  { value: 'trimestre', label: 'Trimestre' },
  { value: 'numeroBandeja', label: 'N° de bandeja' },
  { value: 'slotBandeja', label: 'Slot de bandeja' },
  { value: 'fechaAsignacion', label: 'Fecha de asignación' },
]

function Correcciones({ clientes, ejecutivos, empresas, portas, toast }) {
  const [filtros, setFiltros] = useState({ clienteId: '', ejecutivoId: '', empresaId: '', trimestre: '', link: '' })
  const [resultados, setResultados] = useState(null)
  const [seleccion, setSeleccion] = useState(() => new Set())
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [campo, setCampo] = useState('linkTrello')
  const [valor, setValor] = useState('')
  const [aplicando, setAplicando] = useState(false)
  const [error, setError] = useState('')
  // Round-trip por Excel
  const [descargando, setDescargando] = useState(false)
  const [archivo, setArchivo] = useState(null)
  const [subiendo, setSubiendo] = useState(false)
  const [resultadoImport, setResultadoImport] = useState(null)

  const buscar = async (e) => {
    e?.preventDefault()
    setError('')
    setLoading(true)
    setSeleccion(new Set())
    try {
      const params = {}
      if (filtros.clienteId) params.clienteId = filtros.clienteId
      if (filtros.ejecutivoId) params.ejecutivoId = filtros.ejecutivoId
      if (filtros.empresaId) params.empresaId = filtros.empresaId
      if (filtros.trimestre) params.trimestre = filtros.trimestre.toUpperCase()
      if (filtros.link) params.link = filtros.link
      const data = await buscarAsignaciones(params)
      setResultados(data)
      setPage(1)
    } catch {
      toast.error('No se pudieron buscar las asignaciones')
    } finally {
      setLoading(false)
    }
  }

  const toggle = (id) => setSeleccion((prev) => {
    const next = new Set(prev)
    if (next.has(id)) next.delete(id); else next.add(id)
    return next
  })
  const toggleTodos = () => {
    if (!resultados) return
    setSeleccion((prev) => (prev.size === resultados.length ? new Set() : new Set(resultados.map((a) => a.id))))
  }

  // El "valor" según el campo elegido
  const opcionesValor = {
    clienteId: clientes.map((c) => ({ value: c.id, label: c.razonSocial })),
    ejecutivoId: ejecutivos.map((e) => ({ value: e.id, label: e.nombre })),
    empresaId: empresas.map((e) => ({ value: e.id, label: e.nombre })),
    tipoPortaId: portas.map((p) => ({ value: p.id, label: p.nombre })),
  }
  const esCombo = Boolean(opcionesValor[campo])
  const labelCampo = CAMPOS.find((c) => c.value === campo)?.label || 'valor'

  const aplicar = async () => {
    setError('')
    if (seleccion.size === 0) return setError('Selecciona al menos una asignación')
    if (!valor && valor !== 0) return setError('Indica el nuevo valor')
    setAplicando(true)
    try {
      const n = await correccionMasivaAsignaciones({
        asignacionIds: [...seleccion],
        campo,
        valor: String(valor),
      })
      toast.success(`${n} asignaciones corregidas`)
      setValor('')
      buscar()
    } catch (err) {
      const msg = err.response?.data?.message || 'No se pudo aplicar la corrección'
      setError(msg)
      toast.error(msg)
    } finally {
      setAplicando(false)
    }
  }

  const descargarExcel = async () => {
    if (!resultados?.length) return
    setDescargando(true)
    try {
      const blob = await exportarCorreccionExcel(resultados.map((a) => a.id))
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'correcciones_asignaciones.xlsx'
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      toast.error('No se pudo descargar el Excel')
    } finally {
      setDescargando(false)
    }
  }

  const subirExcel = async () => {
    if (!archivo) return toast.error('Selecciona el archivo .xlsx editado')
    setSubiendo(true)
    setResultadoImport(null)
    try {
      const data = await importarCorreccionExcel(archivo)
      setResultadoImport(data)
      if (data.aplicado === false) {
        toast.error('El archivo tiene errores. No se guardó ningún cambio; corrígelos y vuelve a subirlo.')
      } else {
        toast.success(`${data.actualizadas} asignaciones actualizadas, ${data.sinCambios} sin cambios`)
        buscar()
      }
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo procesar el archivo')
    } finally {
      setSubiendo(false)
    }
  }

  const totalPages = resultados ? Math.ceil(resultados.length / POR_PAGINA_CORR) : 0
  const visibles = resultados ? resultados.slice((page - 1) * POR_PAGINA_CORR, page * POR_PAGINA_CORR) : []

  return (
    <div className="space-y-6">
      <Card title="Filtrar asignaciones">
        <form onSubmit={buscar} className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Combobox
            label="Cliente"
            options={clientes.map((c) => ({ value: c.id, label: c.razonSocial }))}
            value={filtros.clienteId}
            onChange={(v) => setFiltros({ ...filtros, clienteId: v })}
          />
          <Combobox
            label="Ejecutivo"
            options={ejecutivos.map((e) => ({ value: e.id, label: e.nombre }))}
            value={filtros.ejecutivoId}
            onChange={(v) => setFiltros({ ...filtros, ejecutivoId: v })}
          />
          <Combobox
            label="Empresa"
            options={empresas.map((e) => ({ value: e.id, label: e.nombre }))}
            value={filtros.empresaId}
            onChange={(v) => setFiltros({ ...filtros, empresaId: v })}
          />
          <Input label="Trimestre" placeholder="Ej: 2T2026" value={filtros.trimestre} onChange={(e) => setFiltros({ ...filtros, trimestre: e.target.value })} />
          <Input label="Link contiene" value={filtros.link} onChange={(e) => setFiltros({ ...filtros, link: e.target.value })} />
          <div className="flex items-end">
            <Button type="submit">Buscar</Button>
          </div>
        </form>
      </Card>

      {loading && <Loading label="Buscando asignaciones…" />}

      {resultados && (
        <>
          {/* Barra de corrección rápida (mismo valor a las seleccionadas) */}
          <Card title="Corregir seleccionadas">
            <div className="flex flex-wrap items-end gap-3">
              <div className="w-52">
                <Select label="Campo a corregir" value={campo} onChange={(e) => { setCampo(e.target.value); setValor('') }}>
                  {CAMPOS.map((c) => (
                    <option key={c.value} value={c.value}>{c.label}</option>
                  ))}
                </Select>
              </div>
              <div className="w-64">
                {esCombo ? (
                  <Combobox label={`Nuevo ${labelCampo}`} options={opcionesValor[campo]} value={valor} onChange={setValor} />
                ) : campo === 'numeroBandeja' || campo === 'slotBandeja' ? (
                  <Input label={`Nuevo ${labelCampo}`} type="number" value={valor} onChange={(e) => setValor(e.target.value)} />
                ) : campo === 'fechaAsignacion' ? (
                  <Input label="Nueva fecha" type="date" value={valor} onChange={(e) => setValor(e.target.value)} />
                ) : campo === 'trimestre' ? (
                  <Input label="Nuevo trimestre" placeholder="Ej: 2T2026" value={valor} onChange={(e) => setValor(e.target.value)} />
                ) : (
                  <Input label="Nuevo link de Trello" value={valor} onChange={(e) => setValor(e.target.value)} placeholder="https://trello.com/c/…" />
                )}
              </div>
              <Button onClick={aplicar} disabled={aplicando || seleccion.size === 0}>
                {aplicando ? 'Aplicando…' : `Aplicar a ${seleccion.size} seleccionadas`}
              </Button>
            </div>
            {error && <div className="mt-3"><Alert type="error">{error}</Alert></div>}
          </Card>

          {/* Edición por Excel (descargar, editar y volver a subir) */}
          <Card title="Editar por Excel">
            <p className="text-sm text-slate-500 mb-3">
              Descarga las asignaciones encontradas, edítalas en Excel (no cambies la columna
              <b> id_asignacion</b>) y vuelve a subir el archivo para aplicar los cambios. Si hay
              cualquier error, no se guarda nada.
            </p>
            <div className="flex flex-wrap items-center gap-3">
              <Button type="button" variant="secondary" onClick={descargarExcel} disabled={descargando || !resultados.length}>
                {descargando ? 'Descargando…' : `Descargar Excel (${resultados.length})`}
              </Button>
              <input
                type="file"
                accept=".xlsx"
                onChange={(e) => setArchivo(e.target.files[0])}
                className="block text-sm text-slate-600 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:bg-steel file:text-white hover:file:bg-steel/90"
              />
              <Button type="button" onClick={subirExcel} disabled={subiendo}>
                {subiendo ? 'Aplicando…' : 'Aplicar Excel editado'}
              </Button>
            </div>
            {resultadoImport && (
              <div className="mt-4">
                {resultadoImport.aplicado === false && (
                  <Alert type="error">
                    El archivo contiene errores, por lo que <b>no se guardó ningún cambio</b>. Corrígelos y vuelve a subirlo.
                  </Alert>
                )}
                <div className="flex flex-wrap gap-4 text-sm my-3">
                  <span>Total filas: <b>{resultadoImport.totalFilas}</b></span>
                  <span className="text-steel">Actualizadas: <b>{resultadoImport.actualizadas}</b></span>
                  <span className="text-ink/60">Sin cambios: <b>{resultadoImport.sinCambios}</b></span>
                  <span className="text-red-600">Con problemas: <b>{resultadoImport.fallidas}</b></span>
                </div>
                {resultadoImport.errores?.length > 0 && (
                  <div className="bg-red-50 border border-red-200 rounded-lg p-3 max-h-60 overflow-auto">
                    <ul className="text-sm text-red-700 space-y-1">
                      {resultadoImport.errores.map((msg, i) => (<li key={i}>{msg}</li>))}
                    </ul>
                  </div>
                )}
              </div>
            )}
          </Card>

          <Card title={`Asignaciones encontradas (${resultados.length})`}>
            {resultados.length === 0 ? (
              <EmptyState>No hay asignaciones con esos filtros.</EmptyState>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-slate-500 border-b border-slate-200">
                      <th className="py-2 w-8">
                        <input type="checkbox" checked={seleccion.size === resultados.length && resultados.length > 0} onChange={toggleTodos} />
                      </th>
                      <th className="py-2 font-medium">Dosímetro</th>
                      <th className="py-2 font-medium">Cliente</th>
                      <th className="py-2 font-medium">Ejecutivo</th>
                      <th className="py-2 font-medium">Empresa</th>
                      <th className="py-2 font-medium">Porta</th>
                      <th className="py-2 font-medium">Trimestre</th>
                      <th className="py-2 font-medium">Trello</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visibles.map((a) => (
                      <tr key={a.id} className={`border-b border-slate-100 ${seleccion.has(a.id) ? 'bg-steel/5' : ''}`}>
                        <td className="py-2"><input type="checkbox" checked={seleccion.has(a.id)} onChange={() => toggle(a.id)} /></td>
                        <td className="py-2 font-medium text-ink">{a.numeroDosimetro}</td>
                        <td className="py-2 text-slate-600">{a.clienteNombre}</td>
                        <td className="py-2 text-slate-600">{a.ejecutivoNombre}</td>
                        <td className="py-2 text-slate-600">{a.empresaNombre}</td>
                        <td className="py-2 text-slate-600">{a.tipoPortaNombre}</td>
                        <td className="py-2 text-slate-600">{a.trimestre}</td>
                        <td className="py-2 text-slate-600">
                          {a.linkTrello ? <a href={a.linkTrello} target="_blank" rel="noreferrer" className="text-steel hover:underline">link ↗</a> : '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <Pagination page={page} totalPages={totalPages} onChange={setPage} />
                <p className="text-xs text-slate-400 mt-2">
                  La selección para "Corregir seleccionadas" se mantiene entre páginas.
                </p>
              </div>
            )}
          </Card>
        </>
      )}
    </div>
  )
}

function Info({ etiqueta, valor }) {
  return (
    <div className="bg-cream/60 border border-mist/60 rounded-xl px-4 py-2">
      <p className="text-[11px] uppercase tracking-wide text-ink/50">{etiqueta}</p>
      <p className="text-sm font-semibold text-ink">{valor ?? '—'}</p>
    </div>
  )
}

function BotonAccion({ onClick, color, children }) {
  const colores = {
    amber: 'text-amber-600 border-amber-200 hover:bg-amber-50',
    blue: 'text-steel border-steel/30 hover:bg-steel/5',
    steel: 'text-steel border-steel/30 hover:bg-steel/5',
    red: 'text-red-600 border-red-200 hover:bg-red-50',
    slate: 'text-slate-600 border-slate-300 hover:bg-slate-50',
  }
  return (
    <button type="button" onClick={onClick} className={`px-3 py-1.5 rounded-lg border text-sm ${colores[color]}`}>
      {children}
    </button>
  )
}
