import { useState } from 'react'
import { getTareasEliminables, previewSacarRango, sacarDelRango } from '../api/endpoints'
import { Card, Button, Input, Alert, Badge, Modal } from './ui'
import { useToast } from './Toast'
import { useAuth } from '../auth/AuthContext'

/**
 * Sacar por rango: quita de una tarea los dosímetros DISPONIBLES que caen en un
 * rango de bandeja/slot (caso de uso: extravío o daño físico, para no "ensuciar"
 * la tarea). Los dosímetros NO se eliminan ni pierden su historial: quedan
 * disponibles pero sin tarea/bandeja/slot ("limbo"), por lo que dejan de aparecer
 * en Asignar y en Stock hasta que reaparezcan vía "Actualizar stock".
 * Solo Administrador.
 */
export default function SacarPorRango() {
  const { rol } = useAuth()
  const toast = useToast()

  const [filtro, setFiltro] = useState('')
  const [buscando, setBuscando] = useState(false)
  const [tareas, setTareas] = useState([])
  const [tareaSel, setTareaSel] = useState(null) // { tareaId, numeroTarea }

  const [rango, setRango] = useState({ bandejaDesde: '', bandejaHasta: '', slotDesde: '', slotHasta: '' })
  const [preview, setPreview] = useState(null) // { total, sacables, omitidos, items }
  const [cargandoPreview, setCargandoPreview] = useState(false)
  const [confirmar, setConfirmar] = useState(false)
  const [sacando, setSacando] = useState(false)

  if (rol !== 'ADMIN') return null

  const buscar = (e) => {
    if (e) e.preventDefault()
    const q = filtro.trim()
    if (!q) {
      toast.error('Escribe un número de tarea para buscar.')
      return
    }
    setBuscando(true)
    getTareasEliminables(q)
      .then((res) => {
        setTareas(res)
        setTareaSel(null)
        setPreview(null)
      })
      .catch(() => toast.error('No se pudieron cargar las tareas'))
      .finally(() => setBuscando(false))
  }

  const construirPayload = () => {
    if (!tareaSel) return null
    const bd = parseInt(rango.bandejaDesde, 10)
    const bh = parseInt(rango.bandejaHasta, 10)
    if (!bd || !bh) {
      toast.error('Indica la bandeja desde y hasta.')
      return null
    }
    if (bd > bh) {
      toast.error('La bandeja "desde" no puede ser mayor que "hasta".')
      return null
    }
    const sd = rango.slotDesde === '' ? null : parseInt(rango.slotDesde, 10)
    const sh = rango.slotHasta === '' ? null : parseInt(rango.slotHasta, 10)
    if (sd != null && sh != null && sd > sh) {
      toast.error('El slot "desde" no puede ser mayor que "hasta".')
      return null
    }
    return {
      tareaId: tareaSel.tareaId,
      bandejaDesde: bd,
      bandejaHasta: bh,
      slotDesde: sd,
      slotHasta: sh,
    }
  }

  const onPreview = () => {
    const payload = construirPayload()
    if (!payload) return
    setCargandoPreview(true)
    previewSacarRango(payload)
      .then((res) => setPreview(res))
      .catch((err) => toast.error(err.response?.data?.message || 'No se pudo calcular la vista previa'))
      .finally(() => setCargandoPreview(false))
  }

  const onSacar = async () => {
    const payload = construirPayload()
    if (!payload) return
    setSacando(true)
    try {
      const res = await sacarDelRango(payload)
      toast.success(
        `Se sacaron ${res.sacados} dosímetro(s) del rango (quedan como stock en "limbo", sin tarea).` +
          (res.omitidos ? ` Se omitieron ${res.omitidos} por no estar disponibles.` : '')
      )
      setConfirmar(false)
      setPreview(null)
      // Refrescar la vista previa para reflejar el nuevo estado del rango.
      onPreview()
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudieron sacar los dosímetros')
    } finally {
      setSacando(false)
    }
  }

  return (
    <Card title="Sacar dosímetros por rango (extravío / daño)">
      <Alert type="info">
        Quita de una <b>tarea</b> los dosímetros <b>disponibles</b> que caen en un rango de
        bandeja/slot (por ejemplo porque se <b>extraviaron</b> o <b>dañaron</b>), para no
        "ensuciar" la tarea. Los dosímetros <b>no se eliminan</b> ni pierden su historial: quedan
        como stock en <b>"limbo"</b> (disponibles pero sin tarea/bandeja/slot) y <b>dejan de
        aparecer</b> en Asignar y en Stock. Si el dosímetro reaparece, se regulariza con{' '}
        <b>Actualizar stock</b>. Los que no estén disponibles se omiten. Solo Administrador.
      </Alert>

      {/* 1) Buscar y elegir la tarea */}
      <form onSubmit={buscar} className="flex items-end gap-3 mt-4 flex-wrap">
        <div className="flex-1 min-w-[180px]">
          <Input
            label="Buscar por número de tarea"
            placeholder="Ej. 1765"
            value={filtro}
            onChange={(e) => setFiltro(e.target.value)}
          />
        </div>
        <Button type="submit" disabled={buscando}>
          {buscando ? 'Buscando…' : 'Buscar'}
        </Button>
      </form>

      {tareas.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-2">
          {tareas.map((t) => (
            <button
              key={t.tareaId}
              type="button"
              onClick={() => {
                setTareaSel({ tareaId: t.tareaId, numeroTarea: t.numeroTarea })
                setPreview(null)
              }}
              className={`px-3 py-1.5 rounded-lg border text-sm ${
                tareaSel?.tareaId === t.tareaId
                  ? 'border-steel bg-steel/10 text-steel font-semibold'
                  : 'border-slate-200 hover:bg-slate-50'
              }`}
            >
              Tarea {t.numeroTarea} · {t.totalDosimetros} dosím. · {t.disponibles} disp.
            </button>
          ))}
        </div>
      )}

      {/* 2) Rango de bandeja/slot */}
      {tareaSel && (
        <div className="mt-5">
          <p className="text-sm text-ink mb-2">
            Tarea seleccionada: <b>{tareaSel.numeroTarea}</b>
          </p>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            <Input
              label="Bandeja desde"
              type="number"
              min="1"
              value={rango.bandejaDesde}
              onChange={(e) => setRango({ ...rango, bandejaDesde: e.target.value })}
            />
            <Input
              label="Bandeja hasta"
              type="number"
              min="1"
              value={rango.bandejaHasta}
              onChange={(e) => setRango({ ...rango, bandejaHasta: e.target.value })}
            />
            <Input
              label="Slot desde (opcional)"
              type="number"
              min="1"
              value={rango.slotDesde}
              onChange={(e) => setRango({ ...rango, slotDesde: e.target.value })}
            />
            <Input
              label="Slot hasta (opcional)"
              type="number"
              min="1"
              value={rango.slotHasta}
              onChange={(e) => setRango({ ...rango, slotHasta: e.target.value })}
            />
          </div>
          <p className="text-xs text-slate-500 mt-1">
            Deja los slots vacíos para aplicar a toda(s) la(s) bandeja(s) del rango.
          </p>
          <div className="mt-3">
            <Button type="button" variant="secondary" onClick={onPreview} disabled={cargandoPreview}>
              {cargandoPreview ? 'Calculando…' : 'Ver vista previa'}
            </Button>
          </div>
        </div>
      )}

      {/* 3) Vista previa */}
      {preview && (
        <div className="mt-5">
          <div className="flex flex-wrap items-center gap-2 mb-2">
            <Badge color="slate">{preview.total} en el rango</Badge>
            <Badge color="green">{preview.sacables} se sacarán</Badge>
            {preview.omitidos > 0 && <Badge color="amber">{preview.omitidos} se omiten</Badge>}
          </div>
          <div className="border border-slate-200 rounded-lg overflow-hidden">
            <div className="max-h-72 overflow-auto">
              <table className="min-w-full text-sm">
                <thead className="bg-slate-50 text-ink/70 sticky top-0">
                  <tr>
                    <th className="text-left px-3 py-2 font-semibold">Número</th>
                    <th className="text-left px-3 py-2 font-semibold">Tipo</th>
                    <th className="text-right px-3 py-2 font-semibold">Bandeja</th>
                    <th className="text-right px-3 py-2 font-semibold">Slot</th>
                    <th className="text-left px-3 py-2 font-semibold">Estado</th>
                    <th className="text-left px-3 py-2 font-semibold">Acción</th>
                  </tr>
                </thead>
                <tbody>
                  {preview.items.map((it) => (
                    <tr key={it.id} className={`border-t border-slate-100 ${it.sacable ? '' : 'opacity-60'}`}>
                      <td className="px-3 py-2 font-medium">{it.numero}</td>
                      <td className="px-3 py-2">{it.tipoDosimetro}</td>
                      <td className="px-3 py-2 text-right">{it.numeroBandeja}</td>
                      <td className="px-3 py-2 text-right">{it.slotBandeja}</td>
                      <td className="px-3 py-2">{it.estado}</td>
                      <td className="px-3 py-2">
                        {it.sacable ? (
                          <Badge color="green">Se saca</Badge>
                        ) : (
                          <Badge color="slate">Se omite</Badge>
                        )}
                      </td>
                    </tr>
                  ))}
                  {preview.items.length === 0 && (
                    <tr>
                      <td colSpan={6} className="px-3 py-6 text-center text-slate-400">
                        No hay dosímetros en ese rango.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
          <div className="mt-4">
            <Button onClick={() => setConfirmar(true)} disabled={preview.sacables === 0 || sacando}>
              Sacar {preview.sacables} dosímetro(s) del rango
            </Button>
          </div>
        </div>
      )}

      {confirmar && (
        <Modal title="Confirmar sacar por rango" onClose={() => setConfirmar(false)}>
          <p className="text-sm text-ink">
            Se sacarán <b>{preview?.sacables} dosímetro(s)</b> disponibles de la tarea{' '}
            <b>{tareaSel?.numeroTarea}</b> (bandeja {rango.bandejaDesde}–{rango.bandejaHasta}
            {rango.slotDesde || rango.slotHasta ? `, slot ${rango.slotDesde || '·'}–${rango.slotHasta || '·'}` : ''}).
          </p>
          <p className="text-sm text-slate-600 mt-2">
            Quedarán como stock en <b>"limbo"</b> (disponibles, sin tarea/bandeja/slot) y dejarán de
            aparecer en Asignar y Stock. <b>No se elimina</b> ningún dosímetro ni su historial.
          </p>
          <div className="flex gap-2 mt-5">
            <Button onClick={onSacar} disabled={sacando}>
              {sacando ? 'Sacando…' : 'Sí, sacar del rango'}
            </Button>
            <Button variant="secondary" onClick={() => setConfirmar(false)} disabled={sacando}>
              Cancelar
            </Button>
          </div>
        </Modal>
      )}
    </Card>
  )
}
