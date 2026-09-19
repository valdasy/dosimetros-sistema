import { useEffect, useState } from 'react'
import {
  getTiposPorta,
  getTiposDosimetro,
  crearTipoPorta,
  actualizarTipoPorta,
  eliminarTipoPorta,
  getUsoTipoPorta,
} from '../api/endpoints'
import { Card, Button, Input, Select, Badge, Loading, EmptyState } from '../components/ui'
import ConfirmDialog from '../components/ConfirmDialog'
import { useToast } from '../components/Toast'

const VACIO = { nombre: '', tipoDosimetroId: '' }

export default function TiposPorta() {
  const [portas, setPortas] = useState([])
  const [tipos, setTipos] = useState([])
  const [form, setForm] = useState(VACIO)
  const [editId, setEditId] = useState(null)
  const [loading, setLoading] = useState(true)
  // Confirmación de eliminación: { porta, uso } | null; y estado de proceso.
  const [aEliminar, setAEliminar] = useState(null)
  const [procesando, setProcesando] = useState(false)
  const toast = useToast()

  const cargar = () =>
    getTiposPorta()
      .then(setPortas)
      .catch(() => toast.error('No se pudieron cargar los tipos de porta'))
      .finally(() => setLoading(false))

  useEffect(() => {
    cargar()
    getTiposDosimetro().then(setTipos).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const resetForm = () => {
    setForm(VACIO)
    setEditId(null)
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    const payload = { nombre: form.nombre.trim(), tipoDosimetroId: Number(form.tipoDosimetroId) }
    try {
      if (editId) {
        await actualizarTipoPorta(editId, payload)
        toast.success('Tipo de porta actualizado')
      } else {
        await crearTipoPorta(payload)
        toast.success('Tipo de porta creado')
      }
      resetForm()
      cargar()
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo guardar')
    }
  }

  const editar = (p) => {
    setEditId(p.id)
    setForm({ nombre: p.nombre, tipoDosimetroId: String(p.tipoDosimetroId) })
  }

  // Paso 1: consulta el uso y abre el diálogo de confirmación.
  const pedirEliminar = async (p) => {
    try {
      const uso = await getUsoTipoPorta(p.id)
      setAEliminar({ porta: p, uso })
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo verificar el uso')
    }
  }

  // Paso 2: confirma. Si hay histórico, se reasigna a "Sin armar (…)" en el backend.
  const confirmarEliminar = async () => {
    if (!aEliminar) return
    const { porta, uso } = aEliminar
    setProcesando(true)
    try {
      await eliminarTipoPorta(porta.id, uso.total > 0)
      toast.success(
        uso.total > 0
          ? `Porta eliminada; histórico reasignado a "${uso.fallbackNombre}"`
          : 'Tipo de porta eliminado'
      )
      if (editId === porta.id) resetForm()
      setAEliminar(null)
      cargar()
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo eliminar')
    } finally {
      setProcesando(false)
    }
  }

  // Construye el texto de la confirmación según el uso.
  const dialogoEliminar = () => {
    if (!aEliminar) return { mensaje: '', detalle: '', bloqueado: false, tipo: 'error' }
    const { porta, uso } = aEliminar
    if (uso.esSinArmar) {
      return {
        bloqueado: true,
        tipo: 'error',
        mensaje: `La porta "${porta.nombre}" es el estado por defecto "Sin armar" y no se puede eliminar (es donde se conserva el histórico).`,
        detalle: '',
      }
    }
    if (uso.total > 0 && !uso.tieneFallback) {
      return {
        bloqueado: true,
        tipo: 'error',
        mensaje: `"${porta.nombre}" tiene histórico (${uso.dosimetros} dosímetro(s) y ${uso.asignaciones} asignación(es)) pero no existe una porta "Sin armar (${porta.tipoDosimetroNombre})" para conservarlo.`,
        detalle: `Crea primero la porta "Sin armar (${porta.tipoDosimetroNombre})" y vuelve a intentar.`,
      }
    }
    if (uso.total > 0) {
      return {
        bloqueado: false,
        tipo: 'error',
        mensaje: `Vas a eliminar la porta "${porta.nombre}".`,
        detalle: `Hay ${uso.dosimetros} dosímetro(s) y ${uso.asignaciones} asignación(es) usándola. Si continúas, ese histórico NO se pierde: quedará como "${uso.fallbackNombre}".`,
      }
    }
    return {
      bloqueado: false,
      tipo: 'info',
      mensaje: `¿Eliminar la porta "${porta.nombre}"? No tiene dosímetros ni asignaciones asociados.`,
      detalle: '',
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-ink">Tipos de porta</h1>
        <p className="text-sm text-slate-500 mt-0.5">
          Cada porta es compatible con un tipo de dosímetro (OSL, TLD o Cristal).
        </p>
      </div>

      <Card title={editId ? 'Editar tipo de porta' : 'Nuevo tipo de porta'}>
        <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-3 gap-4 items-end">
          <Input
            label="Nombre"
            value={form.nombre}
            onChange={(e) => setForm({ ...form, nombre: e.target.value })}
            required
          />
          <Select
            label="Compatible con (tipo de dosímetro)"
            value={form.tipoDosimetroId}
            onChange={(e) => setForm({ ...form, tipoDosimetroId: e.target.value })}
            required
          >
            <option value="">Selecciona…</option>
            {tipos.map((t) => (
              <option key={t.id} value={t.id}>{t.nombre}</option>
            ))}
          </Select>
          <div className="flex gap-2">
            <Button type="submit">{editId ? 'Guardar cambios' : 'Crear porta'}</Button>
            {editId && (
              <Button type="button" variant="secondary" onClick={resetForm}>
                Cancelar
              </Button>
            )}
          </div>
        </form>
      </Card>

      <Card title={`Tipos de porta (${portas.length})`}>
        {loading ? (
          <Loading />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-slate-500 border-b border-slate-200">
                  <th className="py-2 font-medium">Porta</th>
                  <th className="py-2 font-medium">Compatible con</th>
                  <th className="py-2"></th>
                </tr>
              </thead>
              <tbody>
                {portas.map((p) => (
                  <tr key={p.id} className="border-b border-slate-100">
                    <td className="py-2.5 font-medium text-ink">{p.nombre}</td>
                    <td className="py-2.5">
                      <Badge color="blue">{p.tipoDosimetroNombre}</Badge>
                    </td>
                    <td className="py-2.5 text-right space-x-3">
                      <button onClick={() => editar(p)} className="text-steel hover:underline text-sm">
                        Editar
                      </button>
                      <button onClick={() => pedirEliminar(p)} className="text-red-600 hover:underline text-sm">
                        Eliminar
                      </button>
                    </td>
                  </tr>
                ))}
                {portas.length === 0 && (
                  <tr>
                    <td colSpan="3">
                      <EmptyState>No hay tipos de porta</EmptyState>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {(() => {
        const d = dialogoEliminar()
        return (
          <ConfirmDialog
            open={!!aEliminar}
            title="Eliminar tipo de porta"
            mensaje={d.mensaje}
            detalle={d.detalle}
            detalleTipo={d.tipo}
            confirmLabel={d.bloqueado ? 'Entendido' : 'Eliminar'}
            cancelLabel={d.bloqueado ? 'Cerrar' : 'Cancelar'}
            tone={d.bloqueado ? 'primary' : 'danger'}
            loading={procesando}
            onConfirm={d.bloqueado ? () => setAEliminar(null) : confirmarEliminar}
            onCancel={() => setAEliminar(null)}
          />
        )
      })()}
    </div>
  )
}
