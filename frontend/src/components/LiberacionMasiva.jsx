import { useEffect, useMemo, useState } from 'react'
import { getClientes, previewLiberacion, liberarMasivo } from '../api/endpoints'
import { Card, Button, Input, Alert, Badge, Modal } from './ui'
import { useToast } from './Toast'
import { useAuth } from '../auth/AuthContext'

const TRIMESTRE_REGEX = /^[1-4]T\d{4}$/

// Convierte un input numérico a número o undefined (vacío = sin límite).
const num = (v) => (v === '' || v == null ? undefined : Number(v))

/**
 * Corrección: libera (elimina) asignaciones equivocadas de un cliente en un
 * trimestre, opcionalmente acotadas a una tarea y a un rango de bandeja/slot.
 * Al confirmar, borra esos registros del historial y devuelve los dosímetros a
 * "disponible". Solo Administrador.
 */
export default function LiberacionMasiva() {
  const { rol } = useAuth()
  const toast = useToast()

  const [clientes, setClientes] = useState([])
  const [clienteTexto, setClienteTexto] = useState('')
  const [trimestre, setTrimestre] = useState('')
  const [tareaNumero, setTareaNumero] = useState('')
  const [desdeBandeja, setDesdeBandeja] = useState('')
  const [desdeSlot, setDesdeSlot] = useState('')
  const [hastaBandeja, setHastaBandeja] = useState('')
  const [hastaSlot, setHastaSlot] = useState('')

  const [preview, setPreview] = useState(null) // { total, grupos }
  const [cargando, setCargando] = useState(false)
  const [aplicando, setAplicando] = useState(false)
  const [confirmar, setConfirmar] = useState(false)

  useEffect(() => {
    if (rol === 'ADMIN') getClientes().then(setClientes).catch(() => {})
  }, [rol])

  // Cliente elegido (por razón social exacta del datalist).
  const clienteId = useMemo(() => {
    const t = clienteTexto.trim().toLowerCase()
    const c = clientes.find((x) => x.razonSocial.toLowerCase() === t)
    return c ? c.id : null
  }, [clienteTexto, clientes])

  const hayRango =
    desdeBandeja !== '' || desdeSlot !== '' || hastaBandeja !== '' || hastaSlot !== ''

  // Cualquier cambio en los filtros invalida la vista previa (evita confirmar algo viejo).
  const alCambiar = (setter) => (e) => {
    setter(e.target.value)
    setPreview(null)
  }

  if (rol !== 'ADMIN') return null

  const construirRequest = () => {
    if (!clienteId) {
      toast.error('Elige un cliente válido de la lista.')
      return null
    }
    const tri = trimestre.trim().toUpperCase()
    if (!TRIMESTRE_REGEX.test(tri)) {
      toast.error('El trimestre debe tener el formato 1T2026, 2T2026, etc.')
      return null
    }
    if (hayRango && !tareaNumero.trim()) {
      toast.error('El rango de bandeja/slot requiere indicar una tarea.')
      return null
    }
    return {
      clienteId,
      trimestre: tri,
      tareaNumero: tareaNumero.trim() || undefined,
      desdeBandeja: num(desdeBandeja),
      desdeSlot: num(desdeSlot),
      hastaBandeja: num(hastaBandeja),
      hastaSlot: num(hastaSlot),
    }
  }

  const onPreview = async (e) => {
    e.preventDefault()
    const req = construirRequest()
    if (!req) return
    setCargando(true)
    try {
      setPreview(await previewLiberacion(req))
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo previsualizar')
    } finally {
      setCargando(false)
    }
  }

  const onAplicar = async () => {
    const req = construirRequest()
    if (!req) return
    setAplicando(true)
    try {
      const n = await liberarMasivo(req)
      toast.success(`Se liberaron ${n} asignaciones (dosímetros de vuelta a disponible).`)
      setPreview(null)
      setConfirmar(false)
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo liberar')
    } finally {
      setAplicando(false)
    }
  }

  const rangoGrupo = (g) => {
    if (g.numeroBandeja == null) return 'Sin bandeja'
    const slots = g.slotDesde == null ? '' : ` · slots ${g.slotDesde}–${g.slotHasta}`
    return `Bandeja ${g.numeroBandeja}${slots}`
  }

  return (
    <Card title="Corrección de asignaciones (liberar)">
      <Alert type="info">
        Elimina del historial las asignaciones equivocadas de un <b>cliente</b> en un
        <b> trimestre</b> y devuelve esos dosímetros a <b>disponible</b>. El borrado es
        permanente: usa primero la <b>vista previa</b>. Solo Administrador.
      </Alert>

      <form onSubmit={onPreview} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3 items-end mt-4">
        <Input
          label="Cliente"
          placeholder="Razón social exacta"
          value={clienteTexto}
          onChange={alCambiar(setClienteTexto)}
          list="lib-clientes"
          autoComplete="off"
        />
        <datalist id="lib-clientes">
          {clientes.map((c) => (
            <option key={c.id} value={c.razonSocial} />
          ))}
        </datalist>
        <Input
          label="Trimestre"
          placeholder="Ej. 2T2026"
          value={trimestre}
          onChange={alCambiar(setTrimestre)}
        />
        <Input
          label="Tarea (opcional)"
          placeholder="N° de tarea/repositorio"
          value={tareaNumero}
          onChange={alCambiar(setTareaNumero)}
        />
        <Input
          label="Desde bandeja (opc.)"
          type="number"
          value={desdeBandeja}
          onChange={alCambiar(setDesdeBandeja)}
        />
        <Input
          label="Desde slot (opc.)"
          type="number"
          value={desdeSlot}
          onChange={alCambiar(setDesdeSlot)}
        />
        <Input
          label="Hasta bandeja (opc.)"
          type="number"
          value={hastaBandeja}
          onChange={alCambiar(setHastaBandeja)}
        />
        <Input
          label="Hasta slot (opc.)"
          type="number"
          value={hastaSlot}
          onChange={alCambiar(setHastaSlot)}
        />
        <div>
          <Button type="submit" variant="secondary" disabled={cargando} className="w-full">
            {cargando ? 'Calculando…' : 'Previsualizar'}
          </Button>
        </div>
      </form>

      {preview && (
        <div className="mt-5">
          {preview.total === 0 ? (
            <Alert type="info">No hay asignaciones que coincidan con esos filtros.</Alert>
          ) : (
            <>
              <div className="flex items-center gap-2 mb-2">
                <Badge color="amber">{preview.total} asignaciones</Badge>
                <span className="text-sm text-slate-500">se liberarán:</span>
              </div>
              <div className="border border-slate-200 rounded-lg divide-y divide-slate-100 max-h-64 overflow-auto">
                {preview.grupos.map((g, i) => (
                  <div key={i} className="flex justify-between px-3 py-2 text-sm">
                    <span>
                      Tarea <b>{g.tarea}</b> · {rangoGrupo(g)}
                    </span>
                    <span className="text-slate-500">{g.cantidad}</span>
                  </div>
                ))}
              </div>
              <div className="mt-3">
                <Button onClick={() => setConfirmar(true)} disabled={aplicando}>
                  Liberar {preview.total} asignaciones
                </Button>
              </div>
            </>
          )}
        </div>
      )}

      {confirmar && (
        <Modal title="Confirmar liberación" onClose={() => setConfirmar(false)}>
          <p className="text-sm text-ink">
            Se <b>eliminarán {preview?.total} asignaciones</b> del historial del cliente
            <b> {clienteTexto}</b> en <b>{trimestre.toUpperCase()}</b>
            {tareaNumero.trim() ? <> (tarea <b>{tareaNumero.trim()}</b>)</> : null} y sus
            dosímetros volverán a <b>disponible</b>.
          </p>
          <p className="text-sm text-red-600 mt-2">Esta acción no se puede deshacer.</p>
          <div className="flex gap-2 mt-5">
            <Button onClick={onAplicar} disabled={aplicando}>
              {aplicando ? 'Liberando…' : 'Sí, liberar'}
            </Button>
            <Button variant="secondary" onClick={() => setConfirmar(false)} disabled={aplicando}>
              Cancelar
            </Button>
          </div>
        </Modal>
      )}
    </Card>
  )
}
