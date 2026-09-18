import { useState } from 'react'
import { getTareasEliminables, eliminarTareas } from '../api/endpoints'
import { Card, Button, Input, Alert, Badge, Modal } from './ui'
import { useToast } from './Toast'
import { useAuth } from '../auth/AuthContext'

/**
 * Corrección de carga: elimina por completo tareas equivocadas (y sus dosímetros)
 * subidas por error. Solo se pueden eliminar tareas cuyos dosímetros estén todos
 * disponibles y sin historial de asignación. Solo Administrador. Borrado permanente.
 */
export default function EliminarTareas() {
  const { rol } = useAuth()
  const toast = useToast()

  const [tareas, setTareas] = useState([])
  const [filtro, setFiltro] = useState('')
  const [buscado, setBuscado] = useState(false)
  const [seleccion, setSeleccion] = useState(() => new Set())
  const [cargando, setCargando] = useState(false)
  const [eliminando, setEliminando] = useState(false)
  const [confirmar, setConfirmar] = useState(false)

  // Solo consulta al buscar por número de tarea (no carga todas al entrar a Stock).
  const buscar = (e) => {
    if (e) e.preventDefault()
    const q = filtro.trim()
    if (!q) {
      toast.error('Escribe un número de tarea para buscar.')
      return
    }
    setCargando(true)
    getTareasEliminables(q)
      .then((res) => {
        setTareas(res)
        setBuscado(true)
        setSeleccion(new Set())
      })
      .catch(() => toast.error('No se pudieron cargar las tareas'))
      .finally(() => setCargando(false))
  }

  const seleccionadas = tareas.filter((t) => seleccion.has(t.tareaId))
  const totalDosimetrosSel = seleccionadas.reduce((a, t) => a + t.totalDosimetros, 0)

  if (rol !== 'ADMIN') return null

  const toggle = (t) => {
    if (!t.eliminable) return
    setSeleccion((prev) => {
      const s = new Set(prev)
      if (s.has(t.tareaId)) s.delete(t.tareaId)
      else s.add(t.tareaId)
      return s
    })
  }

  const seleccionarTodas = () => {
    setSeleccion((prev) => {
      const s = new Set(prev)
      tareas.filter((t) => t.eliminable).forEach((t) => s.add(t.tareaId))
      return s
    })
  }

  const limpiar = () => setSeleccion(new Set())

  const onEliminar = async () => {
    setEliminando(true)
    try {
      const res = await eliminarTareas([...seleccion])
      toast.success(`Se eliminaron ${res.tareas} tareas; ${res.dosimetros} dosímetros quedaron como "Sin armar" (pendientes de armado).`)
      setConfirmar(false)
      limpiar()
      buscar()
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudieron eliminar las tareas')
    } finally {
      setEliminando(false)
    }
  }

  return (
    <Card title="Eliminar tareas (corrección de carga)">
      <Alert type="info">
        Elimina la <b>tarea</b> (la agrupación/armado). Los <b>dosímetros se conservan</b>: quedan
        como <b>"Sin armar"</b> (pendientes de armado, no como stock listo), con su historial
        intacto. Solo se pueden eliminar tareas donde <b>no se hayan hecho asignaciones</b> y con
        todos sus dosímetros disponibles. Solo Administrador.
      </Alert>

      <form onSubmit={buscar} className="flex items-end gap-3 mt-4 flex-wrap">
        <div className="flex-1 min-w-[180px]">
          <Input
            label="Buscar por número de tarea"
            placeholder="Ej. 1765"
            value={filtro}
            onChange={(e) => setFiltro(e.target.value)}
          />
        </div>
        <Button type="submit" disabled={cargando}>
          {cargando ? 'Buscando…' : 'Buscar'}
        </Button>
        <Button type="button" variant="secondary" onClick={seleccionarTodas} disabled={tareas.length === 0}>
          Seleccionar todas
        </Button>
        <Button type="button" variant="secondary" onClick={limpiar} disabled={seleccion.size === 0}>
          Limpiar selección
        </Button>
      </form>

      <div className="mt-4 border border-slate-200 rounded-lg overflow-hidden">
        <div className="max-h-80 overflow-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50 text-ink/70 sticky top-0">
              <tr>
                <th className="px-3 py-2 w-10"></th>
                <th className="text-left px-3 py-2 font-semibold">Tarea</th>
                <th className="text-right px-3 py-2 font-semibold">Dosímetros</th>
                <th className="text-right px-3 py-2 font-semibold">Disponibles</th>
                <th className="text-left px-3 py-2 font-semibold">Estado</th>
              </tr>
            </thead>
            <tbody>
              {tareas.map((t) => (
                <tr
                  key={t.tareaId}
                  className={`border-t border-slate-100 ${t.eliminable ? 'cursor-pointer hover:bg-slate-50' : 'opacity-60'}`}
                  onClick={() => toggle(t)}
                >
                  <td className="px-3 py-2 text-center">
                    <input
                      type="checkbox"
                      checked={seleccion.has(t.tareaId)}
                      disabled={!t.eliminable}
                      onChange={() => toggle(t)}
                      onClick={(e) => e.stopPropagation()}
                    />
                  </td>
                  <td className="px-3 py-2 font-medium">{t.numeroTarea}</td>
                  <td className="px-3 py-2 text-right">{t.totalDosimetros}</td>
                  <td className="px-3 py-2 text-right">{t.disponibles}</td>
                  <td className="px-3 py-2">
                    {t.eliminable ? (
                      <Badge color="green">Eliminable</Badge>
                    ) : (
                      <span title={t.motivo || ''}>
                        <Badge color="slate">{t.motivo || 'No eliminable'}</Badge>
                      </span>
                    )}
                  </td>
                </tr>
              ))}
              {tareas.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-3 py-6 text-center text-slate-400">
                    {cargando
                      ? 'Buscando…'
                      : buscado
                        ? 'No se encontraron tareas con ese número.'
                        : 'Busca por número de tarea para ver resultados.'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div className="flex items-center gap-3 mt-4">
        <Badge color={seleccion.size ? 'amber' : 'slate'}>
          {seleccion.size} tareas · {totalDosimetrosSel} dosímetros
        </Badge>
        <Button onClick={() => setConfirmar(true)} disabled={seleccion.size === 0 || eliminando}>
          Eliminar seleccionadas
        </Button>
      </div>

      {confirmar && (
        <Modal title="Confirmar eliminación" onClose={() => setConfirmar(false)}>
          <p className="text-sm text-ink">
            Se eliminarán <b>{seleccion.size} tareas</b>. Sus <b>{totalDosimetrosSel} dosímetros</b>
            {' '}quedarán como <b>"Sin armar"</b> (pendientes de armado; se conserva su historial):
          </p>
          <ul className="text-sm text-slate-600 mt-2 max-h-40 overflow-auto list-disc pl-5">
            {seleccionadas.map((t) => (
              <li key={t.tareaId}>
                Tarea <b>{t.numeroTarea}</b> · {t.totalDosimetros} dosímetros
              </li>
            ))}
          </ul>
          <p className="text-sm text-red-600 mt-3">Esta acción no se puede deshacer.</p>
          <div className="flex gap-2 mt-5">
            <Button onClick={onEliminar} disabled={eliminando}>
              {eliminando ? 'Eliminando…' : 'Sí, eliminar'}
            </Button>
            <Button variant="secondary" onClick={() => setConfirmar(false)} disabled={eliminando}>
              Cancelar
            </Button>
          </div>
        </Modal>
      )}
    </Card>
  )
}
