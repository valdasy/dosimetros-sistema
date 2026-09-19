import { useEffect, useRef, useState } from 'react'
import {
  getClientes,
  crearCliente,
  desactivarCliente,
  getEjecutivos,
  getEmpresas,
  getAsignacionesPorCliente,
} from '../api/endpoints'
import {
  Card,
  Button,
  Input,
  Select,
  Badge,
  Loading,
  EmptyState,
  Pagination,
  Modal,
} from '../components/ui'
import ConfirmDialog from '../components/ConfirmDialog'
import { useToast } from '../components/Toast'

const POR_PAGINA = 20

export default function Clientes() {
  const [clientes, setClientes] = useState([])
  const [ejecutivos, setEjecutivos] = useState([])
  const [empresas, setEmpresas] = useState([])
  const [filtros, setFiltros] = useState({ q: '', ejecutivoId: '', empresaId: '' })
  const [form, setForm] = useState({ razonSocial: '', nombreCorto: '', ejecutivoId: '' })
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(1)
  const [detalle, setDetalle] = useState(null) // { cliente, asignaciones, loading }
  const [aBaja, setABaja] = useState(null) // cliente | null
  const [procesando, setProcesando] = useState(false)
  const toast = useToast()

  const cargar = (f = filtros) => {
    const params = {}
    if (f.q) params.q = f.q
    if (f.ejecutivoId) params.ejecutivoId = f.ejecutivoId
    if (f.empresaId) params.empresaId = f.empresaId
    setLoading(true)
    return getClientes(params)
      .then((data) => {
        setClientes(data)
        setPage(1)
      })
      .catch(() => toast.error('No se pudieron cargar los clientes'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    cargar(filtros)
    getEjecutivos().then(setEjecutivos).catch(() => {})
    getEmpresas().then(setEmpresas).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Búsqueda en vivo: recarga con un pequeño retardo mientras se escribe.
  const primeraCarga = useRef(true)
  useEffect(() => {
    if (primeraCarga.current) {
      primeraCarga.current = false
      return
    }
    const t = setTimeout(() => cargar(filtros), 300)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filtros.q])

  // Cambiar un select de filtro recarga de inmediato.
  const setFiltro = (campo) => (e) => {
    const next = { ...filtros, [campo]: e.target.value }
    setFiltros(next)
    cargar(next)
  }

  const handleCrear = async (e) => {
    e.preventDefault()
    try {
      await crearCliente({
        razonSocial: form.razonSocial,
        nombreCorto: form.nombreCorto || null,
        ejecutivoId: form.ejecutivoId ? Number(form.ejecutivoId) : null,
      })
      setForm({ razonSocial: '', nombreCorto: '', ejecutivoId: '' })
      toast.success('Cliente creado correctamente')
      cargar()
    } catch {
      toast.error('No se pudo crear el cliente')
    }
  }

  const confirmarDesactivar = async () => {
    if (!aBaja) return
    setProcesando(true)
    try {
      await desactivarCliente(aBaja.id)
      toast.success('Cliente desactivado')
      setABaja(null)
      cargar()
    } catch {
      toast.error('No se pudo desactivar')
    } finally {
      setProcesando(false)
    }
  }

  const verDetalle = async (cliente) => {
    setDetalle({ cliente, asignaciones: [], loading: true })
    try {
      const asignaciones = await getAsignacionesPorCliente(cliente.id)
      setDetalle({ cliente, asignaciones, loading: false })
    } catch {
      setDetalle({ cliente, asignaciones: [], loading: false })
      toast.error('No se pudo cargar el detalle')
    }
  }

  const totalPages = Math.ceil(clientes.length / POR_PAGINA)
  const visibles = clientes.slice((page - 1) * POR_PAGINA, page * POR_PAGINA)

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-ink">Clientes</h1>

      <Card title="Nuevo cliente">
        <form onSubmit={handleCrear} className="grid grid-cols-1 md:grid-cols-4 gap-4 items-end">
          <Input
            label="Razón social"
            value={form.razonSocial}
            onChange={(e) => setForm({ ...form, razonSocial: e.target.value })}
            required
          />
          <Input
            label="Nombre fantasía"
            value={form.nombreCorto}
            onChange={(e) => setForm({ ...form, nombreCorto: e.target.value })}
          />
          <Select
            label="Ejecutivo responsable"
            value={form.ejecutivoId}
            onChange={(e) => setForm({ ...form, ejecutivoId: e.target.value })}
          >
            <option value="">Sin asignar</option>
            {ejecutivos.map((ej) => (
              <option key={ej.id} value={ej.id}>{ej.nombre}</option>
            ))}
          </Select>
          <Button type="submit">Crear cliente</Button>
        </form>
      </Card>

      <Card
        title="Filtrar clientes"
        action={
          (filtros.q || filtros.ejecutivoId || filtros.empresaId) && (
            <button
              type="button"
              onClick={() => { const v = { q: '', ejecutivoId: '', empresaId: '' }; setFiltros(v); cargar(v) }}
              className="text-sm text-steel hover:underline"
            >
              Limpiar filtros
            </button>
          )
        }
      >
        <form onSubmit={(e) => { e.preventDefault(); cargar() }}>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="lg:col-span-2">
              <Input
                label="Buscar"
                value={filtros.q}
                onChange={(e) => setFiltros({ ...filtros, q: e.target.value })}
                placeholder="Escribe para filtrar por razón social o fantasía…"
              />
            </div>
            <Select label="Ejecutivo responsable" value={filtros.ejecutivoId} onChange={setFiltro('ejecutivoId')}>
              <option value="">Todos los ejecutivos</option>
              {ejecutivos.map((ej) => (
                <option key={ej.id} value={ej.id}>{ej.nombre}</option>
              ))}
            </Select>
            <Select label="Empresa (con asignaciones)" value={filtros.empresaId} onChange={setFiltro('empresaId')}>
              <option value="">Todas las empresas</option>
              {empresas.map((em) => (
                <option key={em.id} value={em.id}>{em.nombre}</option>
              ))}
            </Select>
          </div>
        </form>
      </Card>

      <Card title={`Clientes (${clientes.length})`}>
        {loading ? (
          <Loading />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-slate-500 border-b border-slate-200">
                  <th className="py-2 font-medium">Razón social</th>
                  <th className="py-2 font-medium">Nombre fantasía</th>
                  <th className="py-2 font-medium">Responsable</th>
                  <th className="py-2 font-medium">Estado asignación</th>
                  <th className="py-2"></th>
                </tr>
              </thead>
              <tbody>
                {visibles.map((c) => (
                  <tr
                    key={c.id}
                    onClick={() => verDetalle(c)}
                    className="border-b border-slate-100 hover:bg-cream/60 cursor-pointer"
                  >
                    <td className="py-2.5 font-medium text-ink">{c.razonSocial}</td>
                    <td className="py-2.5 text-slate-600">{c.nombreCorto || '—'}</td>
                    <td className="py-2.5 text-slate-600">{c.ejecutivoNombre || '—'}</td>
                    <td className="py-2.5">
                      {c.pendienteAsignacion ? (
                        <Badge color="amber">Pendiente de asignación</Badge>
                      ) : (
                        <Badge color="green">Con dosímetros</Badge>
                      )}
                    </td>
                    <td className="py-2.5 text-right">
                      {c.activo && (
                        <button
                          onClick={(e) => {
                            e.stopPropagation()
                            setABaja(c)
                          }}
                          className="text-red-600 hover:underline text-sm"
                        >
                          Desactivar
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                {clientes.length === 0 && (
                  <tr>
                    <td colSpan="5">
                      <EmptyState>No hay clientes registrados</EmptyState>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
            <Pagination page={page} totalPages={totalPages} onChange={setPage} />
          </div>
        )}
      </Card>

      {detalle && (
        <Modal title={detalle.cliente.razonSocial} onClose={() => setDetalle(null)}>
          <div className="flex flex-wrap gap-2 mb-4 text-sm">
            {detalle.cliente.nombreCorto && (
              <Badge color="slate">{detalle.cliente.nombreCorto}</Badge>
            )}
            <Badge color="blue">Responsable: {detalle.cliente.ejecutivoNombre || '—'}</Badge>
            {detalle.cliente.pendienteAsignacion ? (
              <Badge color="amber">Pendiente de asignación</Badge>
            ) : (
              <Badge color="green">Con dosímetros</Badge>
            )}
          </div>

          <h3 className="text-sm font-semibold text-ink mb-2">
            Dosímetros asignados · {detalle.asignaciones.length}
          </h3>

          {detalle.loading ? (
            <Loading />
          ) : detalle.asignaciones.length === 0 ? (
            <EmptyState>Este cliente no tiene dosímetros asignados</EmptyState>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-slate-500 border-b border-slate-200">
                    <th className="py-2 font-medium">N° dosímetro</th>
                    <th className="py-2 font-medium">Trimestre</th>
                    <th className="py-2 font-medium">Fecha</th>
                    <th className="py-2 font-medium">Porta</th>
                    <th className="py-2 font-medium">Empresa</th>
                    <th className="py-2 font-medium">Bandeja/Slot</th>
                  </tr>
                </thead>
                <tbody>
                  {detalle.asignaciones.map((a) => (
                    <tr key={a.id} className="border-b border-slate-100">
                      <td className="py-2 font-medium text-ink">{a.numeroDosimetro}</td>
                      <td className="py-2 text-slate-600">{a.trimestre}</td>
                      <td className="py-2 text-slate-600">{a.fechaAsignacion}</td>
                      <td className="py-2 text-slate-600">{a.tipoPortaNombre}</td>
                      <td className="py-2 text-slate-600">{a.empresaNombre}</td>
                      <td className="py-2 text-slate-600">
                        {a.numeroBandeja != null ? `${a.numeroBandeja} / ${a.slotBandeja}` : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Modal>
      )}

      <ConfirmDialog
        open={!!aBaja}
        title="Desactivar cliente"
        mensaje={aBaja ? `¿Desactivar el cliente "${aBaja.razonSocial}"?` : ''}
        detalle="No se borra su histórico de asignaciones. Dejará de aparecer en los listados de clientes activos, pero su información se conserva."
        detalleTipo="info"
        confirmLabel="Desactivar"
        tone="danger"
        loading={procesando}
        onConfirm={confirmarDesactivar}
        onCancel={() => setABaja(null)}
      />
    </div>
  )
}
