import { useEffect, useState } from 'react'
import {
  getEmpresasChilexpress,
  importarChilexpress,
  buscarChilexpress,
  getPanelChilexpress,
  getClientesChilexpress,
} from '../api/endpoints'
import { Card, Button, Input, Select, Alert, Badge, Loading, EmptyState, Pagination } from '../components/ui'

const PAGE_SIZE = 10
import { useToast } from '../components/Toast'
import { useAuth } from '../auth/AuthContext'

const inputFile =
  'block w-full text-sm text-slate-600 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:bg-steel file:text-white hover:file:bg-steel/90'

async function mensajeError(err, fallback) {
  const data = err.response?.data
  if (data instanceof Blob) {
    try {
      return JSON.parse(await data.text()).message || fallback
    } catch {
      return fallback
    }
  }
  return data?.message || fallback
}

function colorEstado(estado) {
  const e = (estado || '').toUpperCase()
  if (e.includes('DESCARGO') || e.includes('ENTREG')) return 'green' // entregado
  if (e.includes('DEVUEL') || e.includes('EXTRAV') || e.includes('DAÑAD') || e.includes('DANAD') || e.includes('SINIEST') || e.includes('RECHAZ') || e.includes('SOBRANC'))
    return 'red' // problema
  if (e.includes('RECEPCION') && !e.includes('PRE')) return 'blue' // disponible para retiro
  if (e.includes('CONTEN')) return 'amber' // en viaje
  return 'slate' // creada / pre-recepción u otros
}

// Traducción de los estados de Chilexpress a lenguaje claro (tooltip).
function significadoEstado(estado) {
  const e = (estado || '').toUpperCase()
  if (e.includes('DESCARGO')) return 'Entregado'
  if (e.includes('PRE') && e.includes('RECEPCION')) return 'Creada, aún no recibida por Chilexpress'
  if (e.includes('CONTEN')) return 'En viaje al destino'
  if (e.includes('RECEPCION')) return 'Disponible para retiro en sucursal'
  if (e.includes('SOBRANC')) return 'Inconveniente en despacho'
  return ''
}

function TablaOts({ rows }) {
  return (
    <div className="overflow-x-auto border border-slate-200 rounded-lg">
      <table className="min-w-full text-sm">
        <thead className="bg-slate-50 text-ink/70">
          <tr>
            <th className="text-left px-3 py-2 font-semibold">Nro. OT</th>
            <th className="text-left px-3 py-2 font-semibold">Empresa</th>
            <th className="text-left px-3 py-2 font-semibold">Estado</th>
            <th className="text-left px-3 py-2 font-semibold">Destinatario</th>
            <th className="text-left px-3 py-2 font-semibold">Referencia</th>
            <th className="text-left px-3 py-2 font-semibold">Destino</th>
            <th className="text-left px-3 py-2 font-semibold">Fecha entrega</th>
            <th className="text-left px-3 py-2 font-semibold">Receptor</th>
            <th className="text-left px-3 py-2 font-semibold">Cert.</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((o) => (
            <tr key={o.id} className="border-t border-slate-100">
              <td className="px-3 py-2 font-mono text-xs">{o.nroOt}</td>
              <td className="px-3 py-2">{o.empresa}</td>
              <td className="px-3 py-2">
                <span title={significadoEstado(o.estado)}>
                  <Badge color={colorEstado(o.estado)}>{o.estado || '—'}</Badge>
                </span>
              </td>
              <td className="px-3 py-2">{o.nombreDestinatario || '—'}</td>
              <td className="px-3 py-2">{o.nroReferencia || '—'}</td>
              <td className="px-3 py-2">{o.destino || '—'}</td>
              <td className="px-3 py-2 whitespace-nowrap">
                {o.fechaEntrega ? `${o.fechaEntrega}${o.horaEntrega ? ' ' + o.horaEntrega : ''}` : '—'}
              </td>
              <td className="px-3 py-2">{o.receptor || '—'}</td>
              <td className="px-3 py-2">
                {o.certificadoEntrega ? (
                  <a href={o.certificadoEntrega} target="_blank" rel="noreferrer" className="text-steel underline">
                    Ver
                  </a>
                ) : (
                  '—'
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default function Seguimiento() {
  const toast = useToast()
  const { rol } = useAuth()
  const esAdmin = rol === 'ADMIN'

  const [empresas, setEmpresas] = useState([])
  const [panel, setPanel] = useState(null)
  const [panelEmpresa, setPanelEmpresa] = useState('')
  const [pagPend, setPagPend] = useState(1)
  const [clientes, setClientes] = useState([])
  const [cargando, setCargando] = useState(true)

  // Carga (solo admin)
  const [empCarga, setEmpCarga] = useState('')
  const [file, setFile] = useState(null)
  const [importando, setImportando] = useState(false)

  // Búsqueda
  const [empFiltro, setEmpFiltro] = useState('')
  const [q, setQ] = useState('')
  const [estadoFiltro, setEstadoFiltro] = useState('')
  const [fechaTipo, setFechaTipo] = useState('entrega')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [resultados, setResultados] = useState(null)
  const [buscando, setBuscando] = useState(false)

  const cargarPanel = () => {
    getPanelChilexpress().then(setPanel).catch(() => {})
    getClientesChilexpress().then(setClientes).catch(() => {})
    setPagPend(1)
  }

  useEffect(() => {
    Promise.all([getEmpresasChilexpress(), getPanelChilexpress(), getClientesChilexpress()])
      .then(([emps, pnl, clis]) => {
        setEmpresas(emps)
        setPanel(pnl)
        setClientes(clis)
        if (emps.length) {
          setEmpCarga(emps[0])
          setPanelEmpresa(emps[0])
        }
      })
      .catch(() => toast.error('No se pudieron cargar los datos iniciales'))
      .finally(() => setCargando(false))
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const cambiarPanelEmpresa = (emp) => {
    setPanelEmpresa(emp)
    setPagPend(1)
  }

  const importar = async (e) => {
    e.preventDefault()
    if (!file) return toast.error('Selecciona el archivo exportado de Chilexpress.')
    setImportando(true)
    try {
      const res = await importarChilexpress(file, empCarga)
      toast.success(
        `Carga de ${empCarga}: ${res.nuevas} nuevas, ${res.actualizadas} actualizadas (de ${res.total}).`,
      )
      cargarPanel()
      if (resultados !== null) buscar()
    } catch (err) {
      toast.error(await mensajeError(err, 'No se pudo procesar el archivo'))
    } finally {
      setImportando(false)
    }
  }

  const buscar = async (e) => {
    if (e) e.preventDefault()
    // Exige al menos un filtro real (la empresa sola no basta) para no traer todo.
    if (!q.trim() && !estadoFiltro && !desde && !hasta) {
      return toast.error('Ingresa al menos un filtro: cliente, estado o un rango de fecha.')
    }
    setBuscando(true)
    try {
      const params = { fechaTipo }
      if (empFiltro) params.empresa = empFiltro
      if (q.trim()) params.q = q.trim()
      if (estadoFiltro) params.estado = estadoFiltro
      if (desde) params.desde = desde
      if (hasta) params.hasta = hasta
      setResultados(await buscarChilexpress(params))
    } catch (err) {
      toast.error(await mensajeError(err, 'No se pudo buscar'))
    } finally {
      setBuscando(false)
    }
  }

  if (cargando) return <Loading label="Cargando módulo…" />

  // Panel filtrado por la empresa (pestaña) seleccionada.
  const porEmp = (list) => (list || []).filter((o) => o.empresa === panelEmpresa)
  const pRevision = panel ? porEmp(panel.revision) : []
  const pRetiro = panel ? porEmp(panel.retiroSucursal) : []
  const pPend = panel ? porEmp(panel.pendientes) : []
  const totalPagesPend = Math.max(1, Math.ceil(pPend.length / PAGE_SIZE))
  const pendPage = pPend.slice((pagPend - 1) * PAGE_SIZE, pagPend * PAGE_SIZE)

  return (
    <div className="space-y-6 max-w-6xl">
      <div>
        <h1 className="text-2xl font-bold text-ink">Seguimiento Chilexpress</h1>
        <p className="text-sm text-slate-500 mt-0.5">
          Registra y actualiza los números de seguimiento (Órdenes de Transporte) desde el
          Excel exportado del portal de Chilexpress. Módulo independiente del resto del sistema.
        </p>
      </div>

      {panel && (
        <Card
          title="Casos a revisar"
          action={
            <div className="flex gap-2 flex-wrap">
              <Badge color={pRevision.length ? 'red' : 'slate'}>{pRevision.length} con problema</Badge>
              <Badge color={pRetiro.length ? 'blue' : 'slate'}>{pRetiro.length} en sucursal</Badge>
              <Badge color={pPend.length ? 'amber' : 'slate'}>{pPend.length} por entregar</Badge>
            </div>
          }
        >
          {/* Pestañas por empresa (Dosimet / Photomat) */}
          <div className="flex gap-1 mb-4 border-b border-slate-200">
            {empresas.map((emp) => (
              <button
                key={emp}
                type="button"
                onClick={() => cambiarPanelEmpresa(emp)}
                className={`px-4 py-2 text-sm font-medium -mb-px border-b-2 ${
                  panelEmpresa === emp
                    ? 'border-steel text-steel'
                    : 'border-transparent text-slate-500 hover:text-ink'
                }`}
              >
                {emp}
              </button>
            ))}
          </div>

          {pRevision.length === 0 && pRetiro.length === 0 && pPend.length === 0 ? (
            <Alert type="success">
              {panelEmpresa}: nada pendiente — sin encomiendas por entregar, en sucursal ni con problemas. ✅
            </Alert>
          ) : (
            <div className="space-y-5">
              <div>
                <h3 className="text-sm font-semibold text-ink mb-1.5">
                  ⚠️ Con problema (devolución / extraviada / dañada / rechazo / sobrancia) ({pRevision.length})
                </h3>
                {pRevision.length ? (
                  <TablaOts rows={pRevision} />
                ) : (
                  <p className="text-sm text-slate-500">Ninguna.</p>
                )}
              </div>
              <div>
                <h3 className="text-sm font-semibold text-ink mb-1.5">
                  📦 Disponibles para retiro en sucursal ({pRetiro.length})
                </h3>
                {pRetiro.length ? (
                  <TablaOts rows={pRetiro} />
                ) : (
                  <p className="text-sm text-slate-500">Ninguna.</p>
                )}
              </div>
              <div>
                <h3 className="text-sm font-semibold text-ink mb-1.5">
                  ⏳ Pendientes de entrega ({pPend.length})
                </h3>
                {pPend.length ? (
                  <>
                    <TablaOts rows={pendPage} />
                    {totalPagesPend > 1 && (
                      <div className="mt-3">
                        <Pagination page={pagPend} totalPages={totalPagesPend} onChange={setPagPend} />
                      </div>
                    )}
                  </>
                ) : (
                  <p className="text-sm text-slate-500">Ninguna.</p>
                )}
              </div>
            </div>
          )}
        </Card>
      )}

      {esAdmin && (
        <Card title="1. Cargar archivo de Chilexpress">
          <p className="text-sm text-slate-500 mb-3">
            Sube el Excel exportado (rango de fechas). Las OT nuevas se registran y las
            existentes se <b>actualizan</b> en estado y recepción. Puedes subirlo cada semana.
          </p>
          <form onSubmit={importar} className="space-y-4">
            <Select label="Empresa (cuenta)" value={empCarga} onChange={(e) => setEmpCarga(e.target.value)}>
              {empresas.map((emp) => (
                <option key={emp} value={emp}>
                  {emp}
                </option>
              ))}
            </Select>
            <div>
              <label className="block text-sm font-medium text-ink/70 mb-1.5">
                Archivo exportado (.xls / .xlsx)
              </label>
              <input
                type="file"
                accept=".xls,.xlsx,.htm,.html"
                onChange={(e) => setFile(e.target.files[0])}
                className={inputFile}
              />
            </div>
            <Button type="submit" variant="secondary" disabled={importando}>
              {importando ? 'Procesando…' : 'Cargar y actualizar'}
            </Button>
          </form>
        </Card>
      )}

      <Card title={esAdmin ? '2. Buscar órdenes de transporte' : 'Buscar órdenes de transporte'}>
        <form onSubmit={buscar} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3 items-end">
          <Input
            label="Cliente / destinatario / referencia / OT"
            placeholder="Ej. AGROVET, o número de OT"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            list="cx-clientes"
            autoComplete="off"
          />
          <datalist id="cx-clientes">
            {clientes.map((c) => (
              <option key={c} value={c} />
            ))}
          </datalist>
          <Select label="Empresa" value={empFiltro} onChange={(e) => setEmpFiltro(e.target.value)}>
            <option value="">Todas</option>
            {empresas.map((emp) => (
              <option key={emp} value={emp}>
                {emp}
              </option>
            ))}
          </Select>
          <Select label="Estado" value={estadoFiltro} onChange={(e) => setEstadoFiltro(e.target.value)}>
            <option value="">Todos</option>
            <option value="creada">Creada (no recibida)</option>
            <option value="viaje">En viaje</option>
            <option value="retiro">Disponible para retiro</option>
            <option value="entregado">Entregado</option>
            <option value="problema">Con problema</option>
          </Select>
          <Select label="Filtrar fecha por" value={fechaTipo} onChange={(e) => setFechaTipo(e.target.value)}>
            <option value="entrega">Fecha de entrega</option>
            <option value="periodo">Periodo de carga</option>
          </Select>
          <Input label="Desde" type="date" value={desde} onChange={(e) => setDesde(e.target.value)} />
          <Input label="Hasta" type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} />
          <div>
            <Button type="submit" disabled={buscando} className="w-full">
              {buscando ? 'Buscando…' : 'Buscar'}
            </Button>
          </div>
        </form>

        {resultados !== null && (
          <div className="mt-5">
            {resultados.length === 0 ? (
              <EmptyState>Sin resultados para el filtro aplicado.</EmptyState>
            ) : (
              <>
                <p className="text-sm text-slate-500 mb-2">{resultados.length} órdenes.</p>
                <TablaOts rows={resultados} />
              </>
            )}
          </div>
        )}

        {resultados === null && (
          <Alert type="info">
            Ingresa <b>al menos un filtro</b> (cliente, estado o un rango de fecha) y presiona
            <b> Buscar</b>. Se muestran hasta 1000 resultados.
          </Alert>
        )}
      </Card>
    </div>
  )
}
