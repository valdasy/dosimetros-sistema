import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import {
  getUsuarios,
  crearUsuario,
  actualizarUsuario,
  desactivarUsuario,
  getRoles,
  getEjecutivos,
  crearEjecutivo,
  desactivarEjecutivo,
  getUsoEjecutivo,
} from '../api/endpoints'
import { Card, Button, Input, Select, Badge, Loading, EmptyState } from '../components/ui'
import ConfirmDialog from '../components/ConfirmDialog'
import { useToast } from '../components/Toast'

// Módulo unificado de personas del sistema. Se separa en dos pestañas para
// evitar confusiones entre dos conceptos distintos:
//  - Ejecutivos: comerciales que tienen clientes asignados (pueden existir sin
//    cuenta de acceso).
//  - Usuarios: cuentas para iniciar sesión, con un rol. El rol EJECUTIVO se
//    enlaza a un ejecutivo para que ese comercial vea sus clientes.
export default function UsuariosEjecutivos() {
  const { rol } = useAuth()
  const esAdmin = rol === 'ADMIN'
  const [searchParams, setSearchParams] = useSearchParams()

  // El OPERADOR solo administra ejecutivos; la pestaña de usuarios es de ADMIN.
  const tabParam = searchParams.get('tab')
  const tabInicial = tabParam === 'usuarios' && esAdmin ? 'usuarios' : 'ejecutivos'
  const [tab, setTab] = useState(tabInicial)

  const cambiarTab = (k) => {
    setTab(k)
    setSearchParams(k === 'usuarios' ? { tab: 'usuarios' } : {}, { replace: true })
  }

  const tabBtn = (k, label) => (
    <button
      type="button"
      onClick={() => cambiarTab(k)}
      className={`px-4 py-1.5 text-sm ${tab === k ? 'bg-steel text-white' : 'bg-white text-ink/70 hover:bg-mist/20'}`}
    >
      {label}
    </button>
  )

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-ink">Usuarios y ejecutivos</h1>
        <p className="text-sm text-slate-500 mt-0.5">
          <b>Ejecutivos</b>: comerciales que tienen clientes asignados.{' '}
          <b>Usuarios</b>: cuentas para iniciar sesión, con su rol. Un usuario con rol
          EJECUTIVO se enlaza a un ejecutivo para que vea sus clientes.
        </p>
      </div>

      {esAdmin && (
        <div className="inline-flex rounded-lg border border-mist overflow-hidden">
          {tabBtn('ejecutivos', 'Ejecutivos (comerciales)')}
          {tabBtn('usuarios', 'Usuarios de acceso')}
        </div>
      )}

      {tab === 'usuarios' && esAdmin ? <PanelUsuarios /> : <PanelEjecutivos />}
    </div>
  )
}

// --- Pestaña: Ejecutivos (comerciales) ---
function iniciales(nombre) {
  return nombre
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0])
    .join('')
    .toUpperCase()
}

function PanelEjecutivos() {
  const [ejecutivos, setEjecutivos] = useState([])
  const [form, setForm] = useState({ nombre: '', email: '' })
  const [loading, setLoading] = useState(true)
  const [aBaja, setABaja] = useState(null) // { ejecutivo, uso } | null
  const [procesando, setProcesando] = useState(false)
  const toast = useToast()

  const cargar = () =>
    getEjecutivos()
      .then(setEjecutivos)
      .catch(() => toast.error('No se pudieron cargar los ejecutivos'))
      .finally(() => setLoading(false))

  useEffect(() => {
    cargar()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleCrear = async (e) => {
    e.preventDefault()
    try {
      await crearEjecutivo({ nombre: form.nombre, email: form.email || null })
      setForm({ nombre: '', email: '' })
      toast.success('Ejecutivo creado correctamente')
      cargar()
    } catch {
      toast.error('No se pudo crear el ejecutivo')
    }
  }

  const pedirDesactivar = async (ejecutivo) => {
    try {
      const uso = await getUsoEjecutivo(ejecutivo.id)
      setABaja({ ejecutivo, uso })
    } catch {
      toast.error('No se pudo verificar el impacto')
    }
  }

  const confirmarDesactivar = async () => {
    if (!aBaja) return
    setProcesando(true)
    try {
      await desactivarEjecutivo(aBaja.ejecutivo.id)
      toast.success('Ejecutivo desactivado (su histórico se conserva)')
      setABaja(null)
      cargar()
    } catch {
      toast.error('No se pudo desactivar')
    } finally {
      setProcesando(false)
    }
  }

  return (
    <div className="space-y-6">
      <Card title="Nuevo ejecutivo">
        <form onSubmit={handleCrear} className="grid grid-cols-1 md:grid-cols-3 gap-4 items-end">
          <Input
            label="Nombre"
            value={form.nombre}
            onChange={(e) => setForm({ ...form, nombre: e.target.value })}
            required
          />
          <Input
            label="Email"
            type="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
          />
          <Button type="submit">Crear ejecutivo</Button>
        </form>
      </Card>

      <div>
        <h2 className="text-base font-semibold text-ink mb-3">
          Ejecutivos activos ({ejecutivos.length})
        </h2>
        {loading ? (
          <Loading />
        ) : ejecutivos.length === 0 ? (
          <Card>
            <EmptyState>No hay ejecutivos registrados</EmptyState>
          </Card>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {ejecutivos.map((e) => (
              <div
                key={e.id}
                className="bg-white rounded-2xl border border-mist/60 p-5 flex flex-col gap-4"
              >
                <div className="flex items-center gap-3">
                  <div className="w-12 h-12 rounded-full bg-steel/15 text-steel flex items-center justify-center font-bold">
                    {iniciales(e.nombre)}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold text-ink truncate">{e.nombre}</p>
                    <p className="text-xs text-ink/50 truncate">{e.email || 'Sin email'}</p>
                  </div>
                  <Badge color={e.activo ? 'green' : 'red'}>{e.activo ? 'Activo' : 'Inactivo'}</Badge>
                </div>
                {e.activo && (
                  <div className="flex justify-end">
                    <button
                      onClick={() => pedirDesactivar(e)}
                      className="text-sm text-red-600 hover:underline"
                    >
                      Desactivar
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      <ConfirmDialog
        open={!!aBaja}
        title="Desactivar ejecutivo"
        mensaje={aBaja ? `Vas a desactivar al ejecutivo "${aBaja.ejecutivo.nombre}".` : ''}
        detalle={
          aBaja
            ? `Tiene ${aBaja.uso.clientes} cliente(s) a cargo y ${aBaja.uso.asignaciones} asignación(es). ` +
              'El histórico NO se borra: sus clientes y asignaciones seguirán visibles en "Asignaciones por cliente" y "Pendiente de asignación". Solo dejará de aparecer como ejecutivo activo.'
            : ''
        }
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

// --- Pestaña: Usuarios de acceso (solo ADMIN) ---
const VACIO = { username: '', password: '', rolId: '', ejecutivoId: '' }

function PanelUsuarios() {
  const [usuarios, setUsuarios] = useState([])
  const [roles, setRoles] = useState([])
  const [ejecutivos, setEjecutivos] = useState([])
  const [form, setForm] = useState(VACIO)
  const [editId, setEditId] = useState(null)
  const [loading, setLoading] = useState(true)
  const [aBaja, setABaja] = useState(null) // usuario | null
  const [procesando, setProcesando] = useState(false)
  const toast = useToast()

  const cargar = () =>
    getUsuarios()
      .then(setUsuarios)
      .catch(() => toast.error('No se pudieron cargar los usuarios'))
      .finally(() => setLoading(false))

  useEffect(() => {
    cargar()
    getRoles().then(setRoles).catch(() => {})
    getEjecutivos().then(setEjecutivos).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const rolSeleccionado = roles.find((r) => String(r.id) === String(form.rolId))
  const esEjecutivo = rolSeleccionado?.nombre === 'EJECUTIVO'

  const resetForm = () => {
    setForm(VACIO)
    setEditId(null)
  }

  const editar = (u) => {
    const rol = roles.find((r) => r.nombre === u.rol)
    setEditId(u.id)
    setForm({
      username: u.username,
      password: '',
      rolId: rol ? String(rol.id) : '',
      ejecutivoId: u.ejecutivoId ? String(u.ejecutivoId) : '',
    })
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (esEjecutivo && !form.ejecutivoId) {
      toast.error('Selecciona el ejecutivo asociado')
      return
    }
    try {
      if (editId) {
        await actualizarUsuario(editId, {
          rolId: Number(form.rolId),
          ejecutivoId: esEjecutivo && form.ejecutivoId ? Number(form.ejecutivoId) : null,
          password: form.password ? form.password : null,
        })
        toast.success('Usuario actualizado')
      } else {
        await crearUsuario({
          username: form.username.trim(),
          password: form.password,
          rolId: Number(form.rolId),
          ejecutivoId: esEjecutivo && form.ejecutivoId ? Number(form.ejecutivoId) : null,
        })
        toast.success('Usuario creado correctamente')
      }
      resetForm()
      cargar()
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo guardar el usuario')
    }
  }

  const confirmarDesactivar = async () => {
    if (!aBaja) return
    setProcesando(true)
    try {
      await desactivarUsuario(aBaja.id)
      toast.success('Usuario desactivado')
      if (editId === aBaja.id) resetForm()
      setABaja(null)
      cargar()
    } catch {
      toast.error('No se pudo desactivar')
    } finally {
      setProcesando(false)
    }
  }

  const colorRol = { ADMIN: 'blue', OPERADOR: 'green', EJECUTIVO: 'slate' }

  return (
    <div className="space-y-6">
      <Card title={editId ? `Editar usuario: ${form.username}` : 'Nuevo usuario'}>
        <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4 items-end">
          <Input
            label="Usuario"
            value={form.username}
            onChange={(e) => setForm({ ...form, username: e.target.value })}
            required
            disabled={!!editId}
          />
          <Input
            label={editId ? 'Nueva contraseña (opcional)' : 'Contraseña'}
            type="password"
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            required={!editId}
            placeholder={editId ? 'Dejar vacío para mantener' : ''}
          />
          <Select
            label="Rol"
            value={form.rolId}
            onChange={(e) => setForm({ ...form, rolId: e.target.value, ejecutivoId: '' })}
            required
          >
            <option value="">Selecciona…</option>
            {roles.map((r) => (
              <option key={r.id} value={r.id}>{r.nombre}</option>
            ))}
          </Select>
          <Select
            label="Ejecutivo asociado"
            value={form.ejecutivoId}
            onChange={(e) => setForm({ ...form, ejecutivoId: e.target.value })}
            disabled={!esEjecutivo}
          >
            <option value="">{esEjecutivo ? 'Selecciona…' : 'Solo para rol EJECUTIVO'}</option>
            {ejecutivos.map((ej) => (
              <option key={ej.id} value={ej.id}>{ej.nombre}</option>
            ))}
          </Select>
          <div className="flex gap-2">
            <Button type="submit">{editId ? 'Guardar' : 'Crear usuario'}</Button>
            {editId && (
              <Button type="button" variant="secondary" onClick={resetForm}>
                Cancelar
              </Button>
            )}
          </div>
        </form>
      </Card>

      <Card title={`Usuarios activos (${usuarios.length})`}>
        {loading ? (
          <Loading />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-slate-500 border-b border-slate-200">
                  <th className="py-2 font-medium">Usuario</th>
                  <th className="py-2 font-medium">Rol</th>
                  <th className="py-2 font-medium">Ejecutivo</th>
                  <th className="py-2 font-medium">Estado</th>
                  <th className="py-2"></th>
                </tr>
              </thead>
              <tbody>
                {usuarios.map((u) => (
                  <tr key={u.id} className="border-b border-slate-100">
                    <td className="py-2.5 font-medium text-ink">{u.username}</td>
                    <td className="py-2.5">
                      <Badge color={colorRol[u.rol] || 'slate'}>{u.rol}</Badge>
                    </td>
                    <td className="py-2.5 text-slate-600">{u.ejecutivoNombre || '—'}</td>
                    <td className="py-2.5">
                      <Badge color={u.activo ? 'green' : 'red'}>{u.activo ? 'Activo' : 'Inactivo'}</Badge>
                    </td>
                    <td className="py-2.5 text-right space-x-3">
                      <button onClick={() => editar(u)} className="text-steel hover:underline text-sm">
                        Editar
                      </button>
                      {u.activo && (
                        <button
                          onClick={() => setABaja(u)}
                          className="text-red-600 hover:underline text-sm"
                        >
                          Desactivar
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                {usuarios.length === 0 && (
                  <tr>
                    <td colSpan="5">
                      <EmptyState>No hay usuarios</EmptyState>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <ConfirmDialog
        open={!!aBaja}
        title="Desactivar usuario"
        mensaje={aBaja ? `¿Desactivar el usuario "${aBaja.username}"?` : ''}
        detalle="La cuenta ya no podrá iniciar sesión. No se borra nada del histórico y puedes volver a crearla si es necesario."
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
