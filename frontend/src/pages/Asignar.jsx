import { useEffect, useState } from 'react'
import {
  asignarMasivo,
  asignarIndividual,
  getClientes,
  getEjecutivos,
  getEmpresas,
  getTiposPorta,
  getTareasDisponibles,
  getDisponiblesPorNumero,
  importarAsignacionesExcel,
  descargarPlantillaAsignaciones,
  exportarAsignacionesPorIds,
} from '../api/endpoints'
import { Card, Button, Input, Alert, Badge, EmptyState } from '../components/ui'
import Combobox from '../components/Combobox'
import { useToast } from '../components/Toast'
import { construirResumenTexto } from '../lib/resumen'
import { useSearchParams } from 'react-router-dom'

const TRIMESTRE_REGEX = /^[1-4]T\d{4}$/
const hoyISO = () => new Date().toISOString().slice(0, 10)

const DATOS_INICIALES = {
  clienteId: '',
  ejecutivoId: '',
  empresaId: '',
  tipoPortaId: '',
  trimestre: '',
  linkTrello: '',
}

// Normaliza el trimestre a mayúsculas y autocompleta el año si solo escribe "3T".
function normalizaTrimestre(valor) {
  let t = (valor || '').toUpperCase().replace(/\s/g, '')
  if (/^[1-4]T$/.test(t)) t = t + new Date().getFullYear()
  return t
}

export default function Asignar() {
  const [vista, setVista] = useState('masiva') // 'masiva' | 'individual'
  const [clientes, setClientes] = useState([])
  const [ejecutivos, setEjecutivos] = useState([])
  const [empresas, setEmpresas] = useState([])
  const [portas, setPortas] = useState([])

  // Datos comunes a ambas vistas (se conservan al cambiar de pestaña)
  const [datos, setDatos] = useState(DATOS_INICIALES)

  // Masiva
  const [tareas, setTareas] = useState([])
  const [tareasSeleccionadas, setTareasSeleccionadas] = useState([])
  const [cantidad, setCantidad] = useState('')
  const [resumenMasivo, setResumenMasivo] = useState(null)

  // Individual
  const [numero, setNumero] = useState('')
  const [candidatos, setCandidatos] = useState(null) // null=sin buscar, []=sin resultados
  const [dosimetroSel, setDosimetroSel] = useState(null)
  const [resumenIndividual, setResumenIndividual] = useState(null)

  // Por archivo (#12)
  const [archivo, setArchivo] = useState(null)
  const [subiendo, setSubiendo] = useState(false)
  const [validando, setValidando] = useState(false)
  const [resultadoArchivo, setResultadoArchivo] = useState(null)
  const [esValidacion, setEsValidacion] = useState(false)

  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [exportando, setExportando] = useState(false)
  const toast = useToast()

  const copiarResumen = async (asignaciones) => {
    const texto = construirResumenTexto(asignaciones)
    try {
      await navigator.clipboard.writeText(texto)
      toast.success('Resumen copiado (pégalo en Trello)')
    } catch {
      toast.error('No se pudo copiar automáticamente; selecciona y copia el texto de abajo.')
    }
  }

  const exportarResumen = async (asignaciones) => {
    setExportando(true)
    try {
      const blob = await exportarAsignacionesPorIds(asignaciones.map((a) => a.id))
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'asignaciones.xlsx'
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      toast.error('No se pudo exportar')
    } finally {
      setExportando(false)
    }
  }

  const descargarPlantillaArchivo = async () => {
    try {
      const blob = await descargarPlantillaAsignaciones()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'plantilla_asignaciones.xlsx'
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      toast.error('No se pudo descargar la plantilla')
    }
  }

  useEffect(() => {
    getClientes().then(setClientes).catch(() => {})
    getEjecutivos().then(setEjecutivos).catch(() => {})
    getEmpresas().then(setEmpresas).catch(() => {})
    getTiposPorta().then(setPortas).catch(() => {})
  }, [])

  // Precarga desde otra pantalla: /asignar?vista=individual&numero=123
  const [searchParams] = useSearchParams()
  useEffect(() => {
    const v = searchParams.get('vista')
    const n = searchParams.get('numero')
    if (v) setVista(v)
    if (n) {
      setNumero(n)
      getDisponiblesPorNumero(Number(n))
        .then((enc) => {
          setCandidatos(enc)
          if (enc.length === 1) setDosimetroSel(enc[0])
        })
        .catch(() => {})
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Masiva: tareas con disponibles compatibles con el porta elegido.
  const recargarTareas = () => {
    const porta = portas.find((p) => String(p.id) === String(datos.tipoPortaId))
    return getTareasDisponibles(porta ? porta.tipoDosimetroId : undefined)
      .then(setTareas)
      .catch(() => setTareas([]))
  }

  useEffect(() => {
    recargarTareas()
    setTareasSeleccionadas([])
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [datos.tipoPortaId, portas])

  const setDato = (campo) => (e) => setDatos((d) => ({ ...d, [campo]: e.target.value }))
  const setCombo = (campo) => (v) => setDatos((d) => ({ ...d, [campo]: v }))

  const validarComunes = () => {
    if (!datos.clienteId) return 'Selecciona un cliente'
    if (!datos.ejecutivoId) return 'Selecciona un ejecutivo'
    if (!datos.empresaId) return 'Selecciona una empresa'
    if (!datos.tipoPortaId) return 'Selecciona el tipo de porta'
    const trimestre = normalizaTrimestre(datos.trimestre)
    if (!TRIMESTRE_REGEX.test(trimestre)) return 'El trimestre debe tener el formato 1T2026, 2T2026, etc.'
    if (!datos.linkTrello.trim()) return 'El link de Trello es obligatorio'
    return null
  }

  const toggleTarea = (id) => {
    setTareasSeleccionadas((prev) => (prev.includes(id) ? prev.filter((t) => t !== id) : [...prev, id]))
  }

  // Portas compatibles para la vista individual (según el dosímetro encontrado).
  const portasParaSelect =
    vista === 'individual' && dosimetroSel
      ? portas.filter((p) => String(p.tipoDosimetroId) === String(dosimetroSel.tipoDosimetroId))
      : portas

  // --- Campos comunes (cliente, ejecutivo, empresa, porta, trimestre, link) ---
  const CamposComunes = (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      <Combobox
        label="Cliente"
        options={clientes.map((c) => ({ value: c.id, label: c.razonSocial }))}
        value={datos.clienteId}
        onChange={setCombo('clienteId')}
        required
      />
      <Combobox
        label="Ejecutivo"
        options={ejecutivos.map((e) => ({ value: e.id, label: e.nombre }))}
        value={datos.ejecutivoId}
        onChange={setCombo('ejecutivoId')}
        required
      />
      <Combobox
        label="Empresa"
        options={empresas.map((e) => ({ value: e.id, label: e.nombre }))}
        value={datos.empresaId}
        onChange={setCombo('empresaId')}
        required
      />
      <Combobox
        label="Tipo de porta"
        options={portasParaSelect.map((p) => ({ value: p.id, label: p.nombre }))}
        value={datos.tipoPortaId}
        onChange={setCombo('tipoPortaId')}
        required
      />
      <label className="block">
        <span className="block text-sm font-medium text-ink/70 mb-1.5">Trimestre</span>
        <input
          value={datos.trimestre}
          onChange={setDato('trimestre')}
          onBlur={() => setDatos((d) => ({ ...d, trimestre: normalizaTrimestre(d.trimestre) }))}
          placeholder="Ej: 3T  →  3T2026"
          pattern="[1-4]T[0-9]{4}"
          title="Formato: 1T2026, 2T2026, etc."
          className="w-full px-3 py-2 border border-mist rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-steel/40 focus:border-steel transition"
          required
        />
      </label>
      {/* #11 Link de Trello: borde visible y obligatorio */}
      <label className="block">
        <span className="block text-sm font-medium text-ink/70 mb-1.5">
          Link de Trello <span className="text-red-500">*</span>
        </span>
        <input
          value={datos.linkTrello}
          onChange={setDato('linkTrello')}
          placeholder="https://trello.com/c/…"
          className="w-full px-3 py-2 border-2 border-mist rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-steel/40 focus:border-steel transition"
          required
        />
      </label>
    </div>
  )

  // --- Masiva ---
  const totalDisponibles = tareas.reduce((a, t) => a + (t.disponibles || 0), 0)
  const disponiblesSeleccionadas = tareas
    .filter((t) => tareasSeleccionadas.includes(t.id))
    .reduce((a, t) => a + (t.disponibles || 0), 0)

  const onAsignarMasivo = async (e) => {
    e.preventDefault()
    setError('')
    setResumenMasivo(null)
    const errComun = validarComunes()
    if (errComun) return setError(errComun)
    if (!cantidad || Number(cantidad) < 1) return setError('Indica la cantidad a asignar')
    if (tareasSeleccionadas.length === 0) return setError('Selecciona al menos una tarea')

    setLoading(true)
    try {
      const data = await asignarMasivo({
        clienteId: Number(datos.clienteId),
        ejecutivoId: Number(datos.ejecutivoId),
        empresaId: Number(datos.empresaId),
        tipoPortaId: Number(datos.tipoPortaId),
        trimestre: normalizaTrimestre(datos.trimestre),
        cantidad: Number(cantidad),
        tareaIds: tareasSeleccionadas,
        linkTrello: datos.linkTrello.trim(),
      })
      setResumenMasivo(data)
      toast.success(`${data.cantidadAsignada} dosímetros asignados`)
      // #10: refrescar tareas (las que quedaron en 0 desaparecen) y limpiar selección
      setTareasSeleccionadas([])
      setCantidad('')
      recargarTareas()
    } catch (err) {
      const msg = err.response?.data?.message || 'No se pudo realizar la asignación'
      setError(msg)
      toast.error(msg)
    } finally {
      setLoading(false)
    }
  }

  // --- Individual ---
  const buscarNumero = async () => {
    setError('')
    setResumenIndividual(null)
    setDosimetroSel(null)
    if (!numero) return setError('Escribe un número de dosímetro')
    try {
      const encontrados = await getDisponiblesPorNumero(Number(numero))
      setCandidatos(encontrados)
      if (encontrados.length === 1) setDosimetroSel(encontrados[0])
    } catch {
      toast.error('No se pudo buscar el dosímetro')
    }
  }

  const onAsignarIndividual = async (e) => {
    e.preventDefault()
    setError('')
    setResumenIndividual(null)
    if (!dosimetroSel) return setError('Busca y selecciona un dosímetro disponible')
    const errComun = validarComunes()
    if (errComun) return setError(errComun)

    setLoading(true)
    try {
      const data = await asignarIndividual({
        dosimetroId: dosimetroSel.id,
        clienteId: Number(datos.clienteId),
        ejecutivoId: Number(datos.ejecutivoId),
        empresaId: Number(datos.empresaId),
        tipoPortaId: Number(datos.tipoPortaId),
        trimestre: normalizaTrimestre(datos.trimestre),
        fechaAsignacion: hoyISO(),
        linkTrello: datos.linkTrello.trim(),
      })
      setResumenIndividual(data)
      toast.success(`Dosímetro ${data.numeroDosimetro} asignado`)
      // limpiar la búsqueda para asignar el siguiente
      setNumero('')
      setCandidatos(null)
      setDosimetroSel(null)
    } catch (err) {
      const msg = err.response?.data?.message || 'No se pudo asignar el dosímetro'
      setError(msg)
      toast.error(msg)
    } finally {
      setLoading(false)
    }
  }

  const procesarArchivo = async (validar) => {
    setError('')
    setResultadoArchivo(null)
    if (!archivo) return setError('Selecciona un archivo .xlsx')
    if (validar) setValidando(true)
    else setSubiendo(true)
    try {
      const data = await importarAsignacionesExcel(archivo, validar)
      setResultadoArchivo(data)
      setEsValidacion(validar)
      toast.success(
        validar
          ? `Validación: ${data.creados} a crear, ${data.actualizados} a actualizar, ${data.fallidas} con problemas`
          : `Asignaciones: ${data.creados} creadas, ${data.actualizados} actualizadas, ${data.sinCambios} sin cambios`
      )
    } catch (err) {
      const msg = err.response?.data?.message || 'No se pudo procesar el archivo'
      setError(msg)
      toast.error(msg)
    } finally {
      setValidando(false)
      setSubiendo(false)
    }
  }
  const onImportarArchivo = (e) => { e.preventDefault(); procesarArchivo(false) }

  const tabBtn = (k, label) => (
    <button
      type="button"
      onClick={() => { setVista(k); setError('') }}
      className={`px-4 py-1.5 text-sm ${vista === k ? 'bg-steel text-white' : 'bg-white text-ink/70 hover:bg-mist/20'}`}
    >
      {label}
    </button>
  )

  return (
    <div className="space-y-6 max-w-4xl">
      <h1 className="text-2xl font-bold text-ink">Asignación de dosímetros</h1>

      <div className="inline-flex rounded-lg border border-mist overflow-hidden">
        {tabBtn('masiva', 'Masiva (por tareas)')}
        {tabBtn('individual', 'Individual (por número)')}
        {tabBtn('archivo', 'Por archivo')}
      </div>

      {vista !== 'archivo' && <Card title="Datos de la asignación">{CamposComunes}</Card>}

      {vista === 'masiva' && (
        <form onSubmit={onAsignarMasivo} className="space-y-6">
          <Card
            title="Tareas disponibles"
            action={
              <span className="text-sm text-ink/70">
                Total disponible: <b className="text-ink">{totalDisponibles}</b>
              </span>
            }
          >
            <p className="text-sm text-slate-500 mb-3">
              Solo se muestran tareas con dosímetros disponibles
              {datos.tipoPortaId ? ' compatibles con el porta elegido' : ''}. El sistema elige
              automáticamente los dosímetros dentro de las tareas marcadas.
            </p>
            <div className="grid grid-cols-2 md:grid-cols-3 gap-2">
              {tareas.map((t) => (
                <label
                  key={t.id}
                  className={`flex items-center justify-between gap-2 px-3 py-2 rounded-lg border cursor-pointer text-sm ${
                    tareasSeleccionadas.includes(t.id) ? 'border-steel bg-steel/10' : 'border-mist'
                  }`}
                >
                  <span className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      checked={tareasSeleccionadas.includes(t.id)}
                      onChange={() => toggleTarea(t.id)}
                    />
                    {t.numeroTarea}
                  </span>
                  <span className="text-xs text-steel font-medium">{t.disponibles}</span>
                </label>
              ))}
              {tareas.length === 0 && (
                <p className="text-sm text-slate-400">No hay tareas con dosímetros disponibles</p>
              )}
            </div>
            {tareasSeleccionadas.length > 0 && (
              <p className="text-sm text-ink/70 mt-3">
                Seleccionadas: <b>{tareasSeleccionadas.length}</b> tareas ·{' '}
                <b>{disponiblesSeleccionadas}</b> dosímetros disponibles
              </p>
            )}
          </Card>

          <Card title="Cantidad a asignar">
            <div className="max-w-xs">
              <Input
                label="Cantidad"
                type="number"
                min="1"
                max={disponiblesSeleccionadas || undefined}
                value={cantidad}
                onChange={(e) => setCantidad(e.target.value)}
                required
              />
            </div>
          </Card>

          {error && <Alert type="error">{error}</Alert>}

          <Button type="submit" disabled={loading}>
            {loading ? 'Asignando…' : 'Asignar dosímetros'}
          </Button>

          {/* #10: resumen de lo asignado (sin tabla; con exportación) */}
          {resumenMasivo && (
            <Card
              title="Resumen de la asignación"
              action={
                <div className="flex gap-2">
                  <Button
                    variant="secondary"
                    onClick={() => copiarResumen(resumenMasivo.asignaciones)}
                    disabled={!resumenMasivo.asignaciones?.length}
                  >
                    Copiar resumen (Trello)
                  </Button>
                  <Button
                    variant="secondary"
                    onClick={() => exportarResumen(resumenMasivo.asignaciones)}
                    disabled={exportando || !resumenMasivo.asignaciones?.length}
                  >
                    {exportando ? 'Exportando…' : 'Exportar a Excel'}
                  </Button>
                </div>
              }
            >
              <p className="text-sm text-ink/80">
                Se asignaron <b>{resumenMasivo.cantidadAsignada}</b> de{' '}
                {resumenMasivo.cantidadSolicitada} dosímetros solicitados.
              </p>
              {resumenMasivo.asignaciones?.[0] && (
                <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm text-ink/70 mt-3">
                  <span>Cliente: <b className="text-ink">{resumenMasivo.asignaciones[0].clienteNombre}</b></span>
                  <span>Trimestre: <b className="text-ink">{resumenMasivo.asignaciones[0].trimestre}</b></span>
                  <span>Porta: <b className="text-ink">{resumenMasivo.asignaciones[0].tipoPortaNombre}</b></span>
                </div>
              )}
              {resumenMasivo.asignaciones?.length > 0 && (
                <div className="mt-4">
                  <p className="text-xs font-semibold uppercase tracking-wide text-ink/50 mb-1.5">
                    Tareas / bandejas / slots ocupados (para Trello)
                  </p>
                  <pre className="text-xs bg-cream/60 border border-mist/60 rounded-lg p-3 whitespace-pre-wrap max-h-72 overflow-auto text-ink/80 font-mono">
{construirResumenTexto(resumenMasivo.asignaciones)}
                  </pre>
                </div>
              )}
            </Card>
          )}
        </form>
      )}

      {vista === 'individual' && (
        <form onSubmit={onAsignarIndividual} className="space-y-6">
          <Card title="Dosímetro a asignar">
            <div className="flex flex-wrap items-end gap-3">
              <div className="w-48">
                <Input
                  label="Número de dosímetro"
                  type="number"
                  value={numero}
                  onChange={(e) => setNumero(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); buscarNumero() } }}
                />
              </div>
              <Button type="button" variant="secondary" onClick={buscarNumero}>
                Buscar
              </Button>
            </div>

            {candidatos !== null && candidatos.length === 0 && (
              <div className="mt-3">
                <EmptyState>No hay un dosímetro disponible con ese número.</EmptyState>
              </div>
            )}
            {candidatos && candidatos.length > 0 && (
              <div className="mt-3 space-y-2">
                {candidatos.map((d) => (
                  <label
                    key={d.id}
                    className={`flex items-center gap-3 px-3 py-2 rounded-lg border cursor-pointer text-sm ${
                      dosimetroSel?.id === d.id ? 'border-steel bg-steel/10' : 'border-mist'
                    }`}
                  >
                    <input
                      type="radio"
                      name="dosimetroSel"
                      checked={dosimetroSel?.id === d.id}
                      onChange={() => setDosimetroSel(d)}
                    />
                    <span className="font-medium text-ink">{d.numero}</span>
                    <span className="text-slate-500">{d.tipoDosimetroNombre}</span>
                    <span className="text-slate-500">Porta: {d.tipoPortaNombre || '—'}</span>
                    <span className="text-slate-500">Tarea: {d.numeroTarea || '—'}</span>
                    <Badge color="green">disponible</Badge>
                  </label>
                ))}
              </div>
            )}
          </Card>

          {error && <Alert type="error">{error}</Alert>}

          <Button type="submit" disabled={loading || !dosimetroSel}>
            {loading ? 'Asignando…' : 'Asignar dosímetro'}
          </Button>

          {resumenIndividual && (
            <Card title="Resumen de la asignación">
              <ResumenAsignaciones asignaciones={[resumenIndividual]} />
            </Card>
          )}
        </form>
      )}

      {vista === 'archivo' && (
        <form onSubmit={onImportarArchivo} className="space-y-6">
          <Card title="Cargar asignaciones desde archivo">
            <p className="text-sm text-slate-500 mb-3">
              Sube un <b>.xlsx</b> con las columnas: <b>numero_dosimetro, cliente, ejecutivo,
              empresa, tipo_porta, trimestre, link_trello</b> (cliente/ejecutivo/empresa por
              nombre). Se busca por <b>número de dosímetro</b>: si no tiene asignación se crea,
              si algún dato cambió se actualiza y si es idéntica se deja igual. Los números
              duplicados en el sistema o inexistentes se reportan como problema.
            </p>
            <div className="mb-3">
              <Button type="button" variant="secondary" onClick={descargarPlantillaArchivo}>
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
              <Button type="button" variant="secondary" onClick={() => procesarArchivo(true)} disabled={validando || subiendo}>
                {validando ? 'Validando…' : 'Validar'}
              </Button>
              <Button type="submit" disabled={subiendo || validando}>
                {subiendo ? 'Procesando…' : 'Cargar asignaciones'}
              </Button>
            </div>
            <p className="text-xs text-slate-500 mt-2">
              <b>Validar</b> revisa el archivo (columnas y datos) sin aplicar cambios; muestra qué se
              crearía o actualizaría y los problemas por fila. <b>Cargar</b> aplica los cambios.
            </p>
          </Card>

          {error && <Alert type="error">{error}</Alert>}

          {resultadoArchivo && (
            <Card title={esValidacion ? 'Validación (sin aplicar cambios)' : 'Resultado de la carga'}>
              <div className="flex flex-wrap gap-4 text-sm mb-3">
                <span>Total filas: <b>{resultadoArchivo.totalFilas}</b></span>
                <span className="text-emerald-600">Creadas: <b>{resultadoArchivo.creados}</b></span>
                <span className="text-steel">Actualizadas: <b>{resultadoArchivo.actualizados}</b></span>
                <span className="text-ink/60">Sin cambios: <b>{resultadoArchivo.sinCambios}</b></span>
                <span className="text-red-600">Con problemas: <b>{resultadoArchivo.fallidas}</b></span>
              </div>
              {resultadoArchivo.errores?.length > 0 && (
                <div className="bg-red-50 border border-red-200 rounded-lg p-3 max-h-60 overflow-auto">
                  <ul className="text-sm text-red-700 space-y-1">
                    {resultadoArchivo.errores.map((msg, i) => (
                      <li key={i}>{msg}</li>
                    ))}
                  </ul>
                </div>
              )}
            </Card>
          )}
        </form>
      )}
    </div>
  )
}

// Tabla compacta con el detalle de lo asignado (#10).
function ResumenAsignaciones({ asignaciones }) {
  if (!asignaciones || asignaciones.length === 0) return null
  const a0 = asignaciones[0]
  return (
    <div>
      <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm text-ink/70 mb-3">
        <span>Cliente: <b className="text-ink">{a0.clienteNombre}</b></span>
        <span>Ejecutivo: <b className="text-ink">{a0.ejecutivoNombre}</b></span>
        <span>Empresa: <b className="text-ink">{a0.empresaNombre}</b></span>
        <span>Trimestre: <b className="text-ink">{a0.trimestre}</b></span>
        {a0.linkTrello && (
          <a href={a0.linkTrello} target="_blank" rel="noreferrer" className="text-steel hover:underline">
            Ver en Trello ↗
          </a>
        )}
      </div>
      <div className="overflow-x-auto max-h-72 overflow-y-auto">
        <table className="w-full text-sm">
          <thead className="sticky top-0 bg-white">
            <tr className="text-left text-slate-500 border-b border-slate-200">
              <th className="py-2 font-medium">Dosímetro</th>
              <th className="py-2 font-medium">Porta</th>
              <th className="py-2 font-medium">Tarea</th>
              <th className="py-2 font-medium">Bandeja/Slot</th>
            </tr>
          </thead>
          <tbody>
            {asignaciones.map((a) => (
              <tr key={a.id} className="border-b border-slate-100">
                <td className="py-2 font-medium text-ink">{a.numeroDosimetro}</td>
                <td className="py-2 text-slate-600">{a.tipoPortaNombre}</td>
                <td className="py-2 text-slate-600">{a.numeroTarea || '—'}</td>
                <td className="py-2 text-slate-600">
                  {a.numeroBandeja != null ? `${a.numeroBandeja} / ${a.slotBandeja}` : '—'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
