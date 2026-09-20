import { useEffect, useRef, useState } from 'react'
import {
  getClientes,
  crearCliente,
  actualizarCliente,
  desactivarCliente,
  reactivarCliente,
  getUsoCliente,
  eliminarCliente,
  getEjecutivos,
  getEmpresas,
  getResumenPortaTrimestreCliente,
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
  const [filtros, setFiltros] = useState({ q: '', ejecutivoId: '', empresaId: '', incluirInactivos: false })
  const VACIO = { razonSocial: '', nombreCorto: '', rut: '', ejecutivoId: '' }
  const [form, setForm] = useState(VACIO)
  const [editId, setEditId] = useState(null)
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(1)
  const [detalle, setDetalle] = useState(null) // { cliente, resumen, loading }
  const [aBaja, setABaja] = useState(null) // cliente | null
  const [aEliminar, setAEliminar] = useState(null) // { cliente, uso } | null
  const [procesando, setProcesando] = useState(false)
  const toast = useToast()

  const cargar = (f = filtros) => {
    const params = {}
    if (f.q) params.q = f.q
    if (f.ejecutivoId) params.ejecutivoId = f.ejecutivoId
    if (f.empresaId) params.empresaId = f.empresaId
    if (f.incluirInactivos) params.incluirInactivos = true
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

  const resetForm = () => {
    setForm(VACIO)
    setEditId(null)
  }

  const editar = (c) => {
    setEditId(c.id)
    setForm({
      razonSocial: c.razonSocial || '',
      nombreCorto: c.nombreCorto || '',
      rut: c.rut || '',
      ejecutivoId: c.ejecutivoId ? String(c.ejecutivoId) : '',
    })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const handleGuardar = async (e) => {
    e.preventDefault()
    const payload = {
      razonSocial: form.razonSocial,
      nombreCorto: form.nombreCorto || null,
      rut: form.rut || null,
      ejecutivoId: form.ejecutivoId ? Number(form.ejecutivoId) : null,
    }
    try {
      if (editId) {
        await actualizarCliente(editId, payload)
        toast.success('Cliente actualizado')
      } else {
        await crearCliente(payload)
        toast.success('Cliente creado correctamente')
      }
      resetForm()
      cargar()
    } catch (err) {
      toast.error(err.response?.data?.message || (editId ? 'No se pudo actualizar' : 'No se pudo crear el cliente'))
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

  const reactivar = async (c) => {
    try {
      await reactivarCliente(c.id)
      toast.success('Cliente reactivado')
      cargar()
    } catch {
      toast.error('No se pudo reactivar')
    }
  }

  // Eliminar (borrado físico): primero consulta el uso; si tiene asignaciones se
  // bloquea (hay que desactivar en su lugar).
  const pedirEliminar = async (c) => {
    try {
      const uso = await getUsoCliente(c.id)
      setAEliminar({ cliente: c, uso })
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo verificar el cliente')
    }
  }

  const confirmarEliminar = async () => {
    if (!aEliminar) return
    setProcesando(true)
    try {
      await eliminarCliente(aEliminar.cliente.id)
      toast.success('Cliente eliminado')
      setAEliminar(null)
      cargar()
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo eliminar')
    } finally {
      setProcesando(false)
    }
  }

  const verDetalle = async (cliente) => {
    setDetalle({ cliente, resumen: [], loading: true })
    try {
      const resumen = await getResumenPortaTrimestreCliente(cliente.id)
      setDetalle({ cliente, resumen, loading: false })
    } catch {
      setDetalle({ cliente, resumen: [], loading: false })
      toast.error('No se pudo cargar el detalle')
    }
  }

  const totalPages = Math.ceil(clientes.length / POR_PAGINA)
  const visibles = clientes.slice((page - 1) * POR_PAGINA, page * POR_PAGINA)

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-ink">Clientes</h1>

      <Card title={editId ? `Editar cliente: ${form.razonSocial}` : 'Nuevo cliente'}>
        <form onSubmit={handleGuardar} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4 items-end">
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
          <Input
            label="RUT"
            value={form.rut}
            onChange={(e) => setForm({ ...form, rut: e.target.value })}
            placeholder="Ej. 76.123.456-7"
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
          <div className="flex gap-2">
            <Button type="submit">{editId ? 'Guardar cambios' : 'Crear cliente'}</Button>
            {editId && (
              <Button type="button" variant="secondary" onClick={resetForm}>Cancelar</Button>
            )}
          </div>
        </form>
      </Card>

      <Card
        title="Filtrar clientes"
        action={
          (filtros.q || filtros.ejecutivoId || filtros.empresaId || filtros.incluirInactivos) && (
            <button
              type="button"
              onClick={() => { const v = { q: '', ejecutivoId: '', empresaId: '', incluirInactivos: false }; setFiltros(v); cargar(v) }}
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
          <label className="flex items-center gap-2 text-sm text-ink/70 mt-3">
            <input
              type="checkbox"
              checked={filtros.incluirInactivos}
              onChange={(e) => { const v = { ...filtros, incluirInactivos: e.target.checked }; setFiltros(v); cargar(v) }}
            />
            Mostrar clientes inactivos (dados de baja)
          </label>
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
                  <th className="py-2 font-medium">RUT</th>
                  <th className="py-2 font-medium">Responsable</th>
                  <th className="py-2 font-medium">Estado</th>
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
                    <td className="py-2.5 text-slate-600">{c.rut || '—'}</td>
                    <td className="py-2.5 text-slate-600">{c.ejecutivoNombre || '—'}</td>
                    <td className="py-2.5">
                      <Badge color={c.activo ? 'green' : 'red'}>{c.activo ? 'Activo' : 'Inactivo'}</Badge>
                    </td>
                    <td className="py-2.5 text-right space-x-3 whitespace-nowrap">
                      <button
                        onClick={(e) => { e.stopPropagation(); editar(c) }}
                        className="text-steel hover:underline text-sm"
                      >
                        Editar
                      </button>
                      {c.activo ? (
                        <button
                          onClick={(e) => { e.stopPropagation(); setABaja(c) }}
                          className="text-red-600 hover:underline text-sm"
                        >
                          Desactivar
                        </button>
                      ) : (
                        <button
                          onClick={(e) => { e.stopPropagation(); reactivar(c) }}
                          className="text-emerald-600 hover:underline text-sm"
                        >
                          Reactivar
                        </button>
                      )}
                      <button
                        onClick={(e) => { e.stopPropagation(); pedirEliminar(c) }}
                        className="text-red-600 hover:underline text-sm"
                      >
                        Eliminar
                      </button>
                    </td>
                  </tr>
                ))}
                {clientes.length === 0 && (
                  <tr>
                    <td colSpan="6">
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
            {detalle.cliente.rut && (
              <Badge color="slate">RUT: {detalle.cliente.rut}</Badge>
            )}
            <Badge color="blue">Responsable: {detalle.cliente.ejecutivoNombre || '—'}</Badge>
          </div>

          <h3 className="text-sm font-semibold text-ink mb-2">
            Total de dosímetros por trimestre y tipo de porta
          </h3>

          {detalle.loading ? (
            <Loading />
          ) : detalle.resumen.length === 0 ? (
            <EmptyState>Este cliente no tiene dosímetros asignados</EmptyState>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-slate-500 border-b border-slate-200">
                    <th className="py-2 font-medium">Trimestre</th>
                    <th className="py-2 font-medium">Tipo de porta</th>
                    <th className="py-2 font-medium text-right">Cantidad</th>
                  </tr>
                </thead>
                <tbody>
                  {detalle.resumen.map((r, i) => (
                    <tr key={i} className="border-b border-slate-100">
                      <td className="py-2 font-medium text-ink">{r.trimestre}</td>
                      <td className="py-2 text-slate-600">{r.tipoPortaNombre}</td>
                      <td className="py-2 text-right text-ink font-medium">{r.cantidad}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="border-t border-slate-200">
                    <td className="py-2 font-semibold text-ink" colSpan="2">Total</td>
                    <td className="py-2 text-right font-semibold text-ink">
                      {detalle.resumen.reduce((a, r) => a + r.cantidad, 0)}
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          )}
        </Modal>
      )}

      <ConfirmDialog
        open={!!aBaja}
        title="Desactivar cliente"
        mensaje={aBaja ? `¿Desactivar el cliente "${aBaja.razonSocial}"?` : ''}
        detalle="No se borra su histórico de asignaciones. Dejará de aparecer en los listados de clientes activos, pero su información se conserva. Podrás reactivarlo cuando quieras."
        detalleTipo="info"
        confirmLabel="Desactivar"
        tone="danger"
        loading={procesando}
        onConfirm={confirmarDesactivar}
        onCancel={() => setABaja(null)}
      />

      {(() => {
        const bloqueado = aEliminar && aEliminar.uso.asignaciones > 0
        return (
          <ConfirmDialog
            open={!!aEliminar}
            title="Eliminar cliente"
            mensaje={
              !aEliminar ? '' :
              bloqueado
                ? `No se puede eliminar "${aEliminar.cliente.razonSocial}": tiene ${aEliminar.uso.asignaciones} asignación(es) en el histórico.`
                : `¿Eliminar definitivamente el cliente "${aEliminar.cliente.razonSocial}"?`
            }
            detalle={
              !aEliminar ? '' :
              bloqueado
                ? 'Para no perder ese historial, usa "Desactivar" en vez de eliminar.'
                : 'No tiene asignaciones asociadas. Esta acción borra el cliente por completo y no se puede deshacer.'
            }
            detalleTipo={bloqueado ? 'error' : 'info'}
            confirmLabel={bloqueado ? 'Entendido' : 'Eliminar'}
            cancelLabel={bloqueado ? 'Cerrar' : 'Cancelar'}
            tone={bloqueado ? 'primary' : 'danger'}
            loading={procesando}
            onConfirm={bloqueado ? () => setAEliminar(null) : confirmarEliminar}
            onCancel={() => setAEliminar(null)}
          />
        )
      })()}
    </div>
  )
}
