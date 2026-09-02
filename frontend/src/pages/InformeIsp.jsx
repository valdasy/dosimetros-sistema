import { useEffect, useState } from 'react'
import {
  getEmpresasIsp,
  getEstadoIsp,
  importarMaestraIsp,
  analizarInformeIsp,
  generarInformeIsp,
} from '../api/endpoints'
import { Card, Button, Alert, Select, Badge, Loading } from '../components/ui'
import { useToast } from '../components/Toast'

async function mensajeError(err, fallback) {
  const data = err.response?.data
  if (data instanceof Blob) {
    try {
      const txt = await data.text()
      try {
        return JSON.parse(txt).message || fallback
      } catch {
        return txt || fallback
      }
    } catch {
      return fallback
    }
  }
  return err.response?.data?.message || fallback
}

function descargar(blob, nombre) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = nombre
  a.click()
  URL.revokeObjectURL(url)
}

const inputFile =
  'block w-full text-sm text-slate-600 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:bg-steel file:text-white hover:file:bg-steel/90'

export default function InformeIsp() {
  const toast = useToast()
  const [empresas, setEmpresas] = useState([])
  const [estado, setEstado] = useState(null)
  const [cargando, setCargando] = useState(true)

  // Maestra
  const [empMaestra, setEmpMaestra] = useState('')
  const [fileMaestra, setFileMaestra] = useState(null)
  const [importando, setImportando] = useState(false)

  // Informe
  const [empInforme, setEmpInforme] = useState('')
  const [fileInforme, setFileInforme] = useState(null)
  const [analizando, setAnalizando] = useState(false)
  const [generando, setGenerando] = useState(false)
  const [analisis, setAnalisis] = useState(null)
  const [error, setError] = useState('')

  const refrescarEstado = () => getEstadoIsp().then(setEstado).catch(() => {})

  useEffect(() => {
    Promise.all([getEmpresasIsp(), getEstadoIsp()])
      .then(([emps, est]) => {
        setEmpresas(emps)
        setEstado(est)
        if (emps.length) {
          setEmpMaestra(emps[0])
          setEmpInforme(emps[0])
        }
      })
      .catch(() => toast.error('No se pudieron cargar los datos iniciales'))
      .finally(() => setCargando(false))
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const importar = async (e) => {
    e.preventDefault()
    if (!fileMaestra) return toast.error('Selecciona el archivo del informe ISP entregado.')
    setImportando(true)
    try {
      const res = await importarMaestraIsp(fileMaestra, empMaestra)
      toast.success(`Maestra importada: ${res.personas} personas, ${res.clientes} clientes.`)
      await refrescarEstado()
    } catch (err) {
      toast.error(await mensajeError(err, 'No se pudo importar la maestra'))
    } finally {
      setImportando(false)
    }
  }

  const analizar = async (e) => {
    e.preventDefault()
    setError('')
    setAnalisis(null)
    if (!fileInforme) return setError('Selecciona el Informe de Dosis crudo.')
    setAnalizando(true)
    try {
      const res = await analizarInformeIsp(fileInforme, empInforme)
      setAnalisis(res)
    } catch (err) {
      setError(await mensajeError(err, 'No se pudo analizar el informe'))
    } finally {
      setAnalizando(false)
    }
  }

  const generar = async () => {
    if (!fileInforme) return setError('Selecciona el Informe de Dosis crudo.')
    setGenerando(true)
    try {
      const blob = await generarInformeIsp(fileInforme, empInforme)
      descargar(blob, `informe_isp_${empInforme ? empInforme.toLowerCase() : 'salida'}.xlsx`)
      toast.success('Excel del ISP generado y descargado.')
    } catch (err) {
      toast.error(await mensajeError(err, 'No se pudo generar el Excel'))
    } finally {
      setGenerando(false)
    }
  }

  if (cargando) return <Loading label="Cargando módulo ISP…" />

  const maestraVacia = estado && estado.personas === 0 && estado.clientes === 0

  return (
    <div className="space-y-6 max-w-3xl">
      <div>
        <h1 className="text-2xl font-bold text-ink">Informe ISP (RND)</h1>
        <p className="text-sm text-slate-500 mt-0.5">
          Genera el informe dosimétrico trimestral para el ISP a partir del Informe de Dosis crudo,
          asignando automáticamente los códigos de servicio, práctica y cargo.
        </p>
      </div>

      <Card title="Maestras cargadas">
        <div className="flex items-center gap-3 text-sm">
          <Badge color={maestraVacia ? 'amber' : 'green'}>
            {estado ? `${estado.personas} personas` : '—'}
          </Badge>
          <Badge color={maestraVacia ? 'amber' : 'green'}>
            {estado ? `${estado.clientes} clientes` : '—'}
          </Badge>
        </div>
        {maestraVacia && (
          <Alert type="info">
            Aún no hay maestras. Importa un informe ISP ya entregado (p. ej. el 1er Trimestre) para
            que el sistema aprenda los códigos de cargo, práctica y tecnología por RUT y cliente.
          </Alert>
        )}
      </Card>

      <Card title="1. Importar maestra (desde un informe ISP entregado)">
        <p className="text-sm text-slate-500 mb-3">
          Sube un informe ISP ya entregado (hoja <b>DOSIS</b>). El sistema aprende
          <b> RUT → cargo/práctica</b> y <b>cliente → tecnología</b>, y los reutiliza cada trimestre.
        </p>
        <form onSubmit={importar} className="space-y-4">
          <Select label="Empresa" value={empMaestra} onChange={(e) => setEmpMaestra(e.target.value)}>
            {empresas.map((emp) => (
              <option key={emp} value={emp}>
                {emp}
              </option>
            ))}
          </Select>
          <div>
            <label className="block text-sm font-medium text-ink/70 mb-1.5">
              Informe ISP entregado (.xlsm / .xlsx)
            </label>
            <input
              type="file"
              accept=".xlsm,.xlsx"
              onChange={(e) => setFileMaestra(e.target.files[0])}
              className={inputFile}
            />
          </div>
          <Button type="submit" variant="secondary" disabled={importando}>
            {importando ? 'Importando…' : 'Importar maestra'}
          </Button>
        </form>
      </Card>

      <Card title="2. Generar informe ISP (desde el Informe de Dosis crudo)">
        <p className="text-sm text-slate-500 mb-3">
          La <b>tecnología</b> se toma de la columna <code>Tecnologia</code> del informe si
          viene (TLD/OSL/FILM); si no, de la maestra o el default del laboratorio.
          <b> COD CARGO</b> y <b>COD PRAC</b> se completan por <b>empresa + RUT</b> desde la
          maestra del trimestre anterior. Si un RUT no calza, se <b>sugiere</b> por la moda de
          los compañeros del mismo <b>cliente + área</b> (celda <b>pintada</b>, a verificar); si
          no hay referencia, queda <b>en blanco</b>. Todo se lista en la hoja <b>REVISION</b>.
        </p>
        <form onSubmit={analizar} className="space-y-4">
          <Select label="Empresa" value={empInforme} onChange={(e) => setEmpInforme(e.target.value)}>
            {empresas.map((emp) => (
              <option key={emp} value={emp}>
                {emp}
              </option>
            ))}
          </Select>
          <div>
            <label className="block text-sm font-medium text-ink/70 mb-1.5">
              Informe de Dosis crudo (.xlsx)
            </label>
            <input
              type="file"
              accept=".xlsx,.xlsm"
              onChange={(e) => {
                setFileInforme(e.target.files[0])
                setAnalisis(null)
              }}
              className={inputFile}
            />
          </div>

          {error && <Alert type="error">{error}</Alert>}

          <div className="flex gap-3">
            <Button type="submit" variant="secondary" disabled={analizando}>
              {analizando ? 'Analizando…' : 'Analizar'}
            </Button>
            <Button type="button" onClick={generar} disabled={generando}>
              {generando ? 'Generando…' : 'Generar y descargar Excel ISP'}
            </Button>
          </div>
        </form>
      </Card>

      {analisis && (
        <Card title="Vista previa del análisis">
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-4">
            {Object.entries(analisis.resumen).map(([k, v]) => (
              <div key={k} className="rounded-lg bg-slate-50 border border-slate-200 p-3">
                <div className="text-lg font-bold text-ink">{v}</div>
                <div className="text-xs text-slate-500">{etiqueta(k)}</div>
              </div>
            ))}
          </div>

          {analisis.inconsistencias.length > 0 ? (
            <div className="overflow-x-auto">
              <p className="text-sm text-slate-500 mb-2">
                Inconsistencias para revisar en el software de origen (no bloquean la generación; van
                también en la hoja <b>REVISION</b> del Excel):
              </p>
              <table className="min-w-full text-sm">
                <thead>
                  <tr className="text-left text-slate-500 border-b">
                    <th className="py-1.5 pr-3">Fila</th>
                    <th className="py-1.5 pr-3">RUT</th>
                    <th className="py-1.5 pr-3">Usuario</th>
                    <th className="py-1.5 pr-3">Tipo</th>
                    <th className="py-1.5 pr-3">Detalle</th>
                  </tr>
                </thead>
                <tbody>
                  {analisis.inconsistencias.slice(0, 200).map((inc, i) => (
                    <tr key={i} className="border-b border-slate-100">
                      <td className="py-1.5 pr-3">{inc.filaExcel}</td>
                      <td className="py-1.5 pr-3">{inc.rut}</td>
                      <td className="py-1.5 pr-3">{inc.usuario}</td>
                      <td className="py-1.5 pr-3">
                        <Badge color="amber">{inc.tipo}</Badge>
                      </td>
                      <td className="py-1.5 pr-3">{inc.detalle}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {analisis.inconsistencias.length > 200 && (
                <p className="text-xs text-slate-400 mt-2">
                  Mostrando 200 de {analisis.inconsistencias.length}. El Excel las incluye todas.
                </p>
              )}
            </div>
          ) : (
            <Alert type="success">Sin inconsistencias detectadas.</Alert>
          )}
        </Card>
      )}
    </div>
  )
}

function etiqueta(k) {
  const m = {
    filasTotales: 'Filas totales',
    filasEliminadas: 'Filas eliminadas',
    filasProcesadas: 'Filas procesadas',
    personasUnicas: 'Personas únicas',
    inconsistencias: 'Inconsistencias',
  }
  return m[k] || k
}
