import { useEffect, useState } from 'react'
import {
  getEmpresasChilexpress,
  importarChilexpress,
  buscarChilexpress,
  getPanelChilexpress,
} from '../api/endpoints'
import { Card, Button, Input, Select, Alert, Badge, Loading, EmptyState } from '../components/ui'
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
  if (e.includes('ENTREG')) return 'green'
  if (e.includes('DEVUEL') || e.includes('DESCARGO')) return 'amber'
  return 'slate'
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
                <Badge color={colorEstado(o.estado)}>{o.estado || '—'}</Badge>
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
  const [cargando, setCargando] = useState(true)

  // Carga (solo admin)
  const [empCarga, setEmpCarga] = useState('')
  const [file, setFile] = useState(null)
  const [importando, setImportando] = useState(false)

  // Búsqueda
  const [empFiltro, setEmpFiltro] = useState('')
  const [q, setQ] = useState('')
  const [fechaTipo, setFechaTipo] = useState('entrega')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [resultados, setResultados] = useState(null)
  const [buscando, setBuscando] = useState(false)

  const cargarPanel = () => getPanelChilexpress().then(setPanel).catch(() => {})

  useEffect(() => {
    Promise.all([getEmpresasChilexpress(), getPanelChilexpress()])
      .then(([emps, pnl]) => {
        setEmpresas(emps)
        setPanel(pnl)
        if (emps.length) setEmpCarga(emps[0])
      })
      .catch(() => toast.error('No se pudieron cargar los datos iniciales'))
      .finally(() => setCargando(false))
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

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
    setBuscando(true)
    try {
      const params = { fechaTipo }
      if (empFiltro) params.empresa = empFiltro
      if (q.trim()) params.q = q.trim()
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
              <Badge color={panel.revision.length ? 'red' : 'slate'}>
                {panel.revision.length} con problema
              </Badge>
              <Badge color={panel.retiroSucursal.length ? 'blue' : 'slate'}>
                {panel.retiroSucursal.length} en sucursal
              </Badge>
              <Badge color={panel.pendientes.length ? 'amber' : 'slate'}>
                {panel.pendientes.length} por entregar
              </Badge>
            </div>
          }
        >
          {panel.revision.length === 0 &&
          panel.retiroSucursal.length === 0 &&
          panel.pendientes.length === 0 ? (
            <Alert type="success">Nada pendiente: sin encomiendas por entregar, en sucursal ni con problemas. ✅</Alert>
          ) : (
            <div className="space-y-5">
              <div>
                <h3 className="text-sm font-semibold text-ink mb-1.5">
                  ⚠️ Con problema (devolución / extraviada / dañada / rechazo) ({panel.revision.length})
                </h3>
                {panel.revision.length ? (
                  <TablaOts rows={panel.revision} />
                ) : (
                  <p className="text-sm text-slate-500">Ninguna.</p>
                )}
              </div>
              <div>
                <h3 className="text-sm font-semibold text-ink mb-1.5">
                  📦 Disponibles para retiro en sucursal ({panel.retiroSucursal.length})
                </h3>
                {panel.retiroSucursal.length ? (
                  <TablaOts rows={panel.retiroSucursal} />
                ) : (
                  <p className="text-sm text-slate-500">Ninguna.</p>
                )}
              </div>
              <div>
                <h3 className="text-sm font-semibold text-ink mb-1.5">
                  ⏳ Pendientes de entrega ({panel.pendientes.length})
                </h3>
                {panel.pendientes.length ? (
                  <TablaOts rows={panel.pendientes} />
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
          />
          <Select label="Empresa" value={empFiltro} onChange={(e) => setEmpFiltro(e.target.value)}>
            <option value="">Todas</option>
            {empresas.map((emp) => (
              <option key={emp} value={emp}>
                {emp}
              </option>
            ))}
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
            Ingresa un texto y/o un rango de fechas y presiona <b>Buscar</b>. Puedes dejar el
            texto vacío para ver todas las órdenes del rango.
          </Alert>
        )}
      </Card>
    </div>
  )
}
