import client from './client'

// --- Autenticación ---
export const login = (username, password) =>
  client.post('/auth/login', { username, password }).then((r) => r.data)

// --- Usuarios ---
export const getRoles = () => client.get('/roles').then((r) => r.data)
export const getUsuarios = () => client.get('/usuarios').then((r) => r.data)
export const crearUsuario = (data) => client.post('/usuarios', data).then((r) => r.data)
export const actualizarUsuario = (id, data) =>
  client.put(`/usuarios/${id}`, data).then((r) => r.data)
export const desactivarUsuario = (id) => client.patch(`/usuarios/${id}/desactivar`)

// --- Catálogos ---
export const getEmpresas = () => client.get('/empresas').then((r) => r.data)
export const getTiposDosimetro = () => client.get('/tipos-dosimetro').then((r) => r.data)
export const getTiposPorta = () => client.get('/tipos-porta').then((r) => r.data)
export const crearTipoPorta = (data) => client.post('/tipos-porta', data).then((r) => r.data)
export const actualizarTipoPorta = (id, data) =>
  client.put(`/tipos-porta/${id}`, data).then((r) => r.data)
export const getUsoTipoPorta = (id) => client.get(`/tipos-porta/${id}/uso`).then((r) => r.data)
export const eliminarTipoPorta = (id, confirmar = false) =>
  client.delete(`/tipos-porta/${id}`, { params: { confirmar } })
export const getTareas = () => client.get('/tareas').then((r) => r.data)
export const getTareasDisponibles = (tipoDosimetroId) =>
  client
    .get('/dosimetros/tareas-disponibles', {
      params: tipoDosimetroId ? { tipoDosimetroId } : {},
    })
    .then((r) => r.data)

// --- Dashboard ---
export const getKpis = (trimestre) =>
  client.get('/dashboard/kpis', { params: trimestre ? { trimestre } : {} }).then((r) => r.data)
export const getStockHistorico = (fecha) =>
  client
    .get('/dashboard/stock-historico', { params: fecha ? { fecha } : {} })
    .then((r) => r.data)

// --- Dosímetros ---
export const getStock = (params) =>
  client.get('/dosimetros/stock', { params }).then((r) => r.data)
export const getDisponibles = () => client.get('/dosimetros/disponibles').then((r) => r.data)
export const getPortasDisponibles = () =>
  client.get('/dosimetros/disponibles/portas').then((r) => r.data)
export const getStockPortas = () =>
  client.get('/dosimetros/stock/portas').then((r) => r.data)
export const getStockMatriz = (params) =>
  client.get('/dosimetros/stock/matriz', { params }).then((r) => r.data)
export const actualizarStockExcel = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return client.post('/dosimetros/importacion/actualizar', formData).then((r) => r.data)
}
export const descargarPlantillaDosimetros = () =>
  client.get('/dosimetros/importacion/plantilla', { responseType: 'blob' }).then((r) => r.data)
export const exportarStockExcel = (params) =>
  client.get('/dosimetros/stock/export', { params, responseType: 'blob' }).then((r) => r.data)
export const buscarDosimetro = (numero) =>
  client.get('/dosimetros/buscar', { params: { numero } }).then((r) => r.data)
export const getDetalleDosimetros = (numero) =>
  client.get('/dosimetros/detalle', { params: { numero } }).then((r) => r.data)
export const editarEspecificaciones = (id, data) =>
  client.patch(`/dosimetros/${id}/especificaciones`, data).then((r) => r.data)
export const getDuplicados = () => client.get('/dosimetros/duplicados').then((r) => r.data)
export const getHistorial = (id) =>
  client.get(`/dosimetros/${id}/historial`).then((r) => r.data)
export const marcarStockEmergencia = (id, observacion) =>
  client
    .patch(`/dosimetros/${id}/stock-emergencia`, null, { params: { observacion } })
    .then((r) => r.data)
export const darDeBaja = (id, observacion) =>
  client.patch(`/dosimetros/${id}/baja`, null, { params: { observacion } }).then((r) => r.data)
export const liberarDosimetro = (id) =>
  client.patch(`/dosimetros/${id}/liberar`).then((r) => r.data)
// Corrección de carga: listar tareas eliminables y eliminarlas (con sus dosímetros).
export const getTareasEliminables = (q) =>
  client.get('/dosimetros/tareas/eliminables', { params: { q } }).then((r) => r.data)
export const eliminarTareas = (tareaIds) =>
  client.post('/dosimetros/tareas/eliminar', tareaIds).then((r) => r.data)
export const marcarDanado = (id) =>
  client.patch(`/dosimetros/${id}/danado`).then((r) => r.data)
export const marcarBueno = (id) =>
  client.patch(`/dosimetros/${id}/bueno`).then((r) => r.data)
export const marcarExtraviado = (id, observacion) =>
  client.patch(`/dosimetros/${id}/extraviado`, null, { params: { observacion } }).then((r) => r.data)
export const actualizarTipoPortaRango = (data) =>
  client.patch('/dosimetros/rango-porta', data).then((r) => r.data)
export const getResumenArmadoTareas = () =>
  client.get('/dosimetros/tareas/resumen-armado').then((r) => r.data)
export const getDosimetrosDeTarea = (tareaId) =>
  client.get(`/dosimetros/tareas/${tareaId}/dosimetros`).then((r) => r.data)
export const armarSeleccion = (data) =>
  client.patch('/dosimetros/porta-seleccion', data).then((r) => r.data)

// --- Clientes ---
export const getClientes = (params) => client.get('/clientes', { params }).then((r) => r.data)
export const crearCliente = (data) => client.post('/clientes', data).then((r) => r.data)
export const actualizarCliente = (id, data) =>
  client.put(`/clientes/${id}`, data).then((r) => r.data)
export const desactivarCliente = (id) => client.patch(`/clientes/${id}/desactivar`)
export const getAsignacionesPorCliente = (clienteId) =>
  client.get(`/asignaciones/cliente/${clienteId}`).then((r) => r.data)
// Trimestres (distintos) de un cliente, para los chips de filtro sin traer todo.
export const getTrimestresCliente = (clienteId) =>
  client.get(`/asignaciones/cliente/${clienteId}/trimestres`).then((r) => r.data)
export const getMisTrimestresCliente = (clienteId) =>
  client.get(`/ejecutivo/clientes/${clienteId}/trimestres`).then((r) => r.data)

// --- Ejecutivos ---
export const getEjecutivos = () => client.get('/ejecutivos').then((r) => r.data)
export const crearEjecutivo = (data) => client.post('/ejecutivos', data).then((r) => r.data)
export const actualizarEjecutivo = (id, data) =>
  client.put(`/ejecutivos/${id}`, data).then((r) => r.data)
export const getUsoEjecutivo = (id) => client.get(`/ejecutivos/${id}/uso`).then((r) => r.data)
export const desactivarEjecutivo = (id) => client.patch(`/ejecutivos/${id}/desactivar`)

// --- Asignaciones ---
export const asignarMasivo = (data) =>
  client.post('/asignaciones/masivo', data).then((r) => r.data)
// Corrección: liberar (eliminar) asignaciones por cliente + trimestre (+ tarea y rango).
export const previewLiberacion = (data) =>
  client.post('/asignaciones/liberacion/preview', data).then((r) => r.data)
export const liberarMasivo = (data) =>
  client.post('/asignaciones/liberacion', data).then((r) => r.data)
export const asignarIndividual = (data) =>
  client.post('/asignaciones', data).then((r) => r.data)
export const buscarAsignaciones = (params) =>
  client.get('/asignaciones/buscar', { params }).then((r) => r.data)
export const getPendientesEnvio = (params) =>
  client.get('/asignaciones/pendientes-envio', { params }).then((r) => r.data)
export const marcarEnvioAsignaciones = (data) =>
  client.patch('/asignaciones/envio', data).then((r) => r.data)
export const getMisPendientesEnvio = (params) =>
  client.get('/ejecutivo/pendientes-envio', { params }).then((r) => r.data)
export const getResumenClienteTrimestre = (params) =>
  client.get('/asignaciones/resumen-cliente-trimestre', { params }).then((r) => r.data)
export const getMisResumenClienteTrimestre = () =>
  client.get('/ejecutivo/resumen-cliente-trimestre').then((r) => r.data)
export const getResumenClienteTrimestrePorta = (params) =>
  client.get('/asignaciones/resumen-cliente-trimestre-porta', { params }).then((r) => r.data)
export const getMisResumenClienteTrimestrePorta = () =>
  client.get('/ejecutivo/resumen-cliente-trimestre-porta').then((r) => r.data)
export const correccionMasivaAsignaciones = (data) =>
  client.patch('/asignaciones/correccion-masiva', data).then((r) => r.data)
export const exportarCorreccionExcel = (ids) =>
  client.post('/asignaciones/correcciones/exportar', ids, { responseType: 'blob' }).then((r) => r.data)
export const importarCorreccionExcel = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return client.post('/asignaciones/correcciones/importar', formData).then((r) => r.data)
}
export const editarAsignacion = (id, data) =>
  client.patch(`/asignaciones/${id}`, data).then((r) => r.data)
export const getDisponiblesPorNumero = (numero) =>
  client.get('/dosimetros/disponible-por-numero', { params: { numero } }).then((r) => r.data)
export const importarAsignacionesExcel = (file, validar = false) => {
  const formData = new FormData()
  formData.append('file', file)
  return client
    .post('/asignaciones/importacion', formData, { params: { validar } })
    .then((r) => r.data)
}
export const descargarPlantillaAsignaciones = () =>
  client.get('/asignaciones/importacion/plantilla', { responseType: 'blob' }).then((r) => r.data)
export const exportarAsignacionesPorIds = (ids) =>
  client.post('/asignaciones/exportar', ids, { responseType: 'blob' }).then((r) => r.data)

// --- Vistas del ejecutivo ---
export const getMisClientes = () => client.get('/ejecutivo/mis-clientes').then((r) => r.data)
export const getMisAsignaciones = (params) =>
  client.get('/ejecutivo/mis-asignaciones', { params }).then((r) => r.data)
export const getMisFiltros = () => client.get('/ejecutivo/mis-filtros').then((r) => r.data)
export const exportarMisAsignacionesExcel = (params) =>
  client
    .get('/ejecutivo/mis-asignaciones/export', { params, responseType: 'blob' })
    .then((r) => r.data)
export const getMisLotes = () => client.get('/ejecutivo/mis-lotes').then((r) => r.data)

// --- Comparador de dosimetría (ejecutivo) ---
export const compararDosimetria = (softwareFile, clientFile) => {
  const fd = new FormData()
  fd.append('softwareFile', softwareFile)
  fd.append('clientFile', clientFile)
  return client.post('/ejecutivo/comparador', fd, { responseType: 'blob' }).then((r) => r.data)
}
export const descargarPlantillaComparador = () =>
  client.get('/ejecutivo/comparador/plantilla', { responseType: 'blob' }).then((r) => r.data)

// --- Informe ISP (Registro Nacional de Dosis) ---
// Módulo autocontenido: las empresas (laboratorios) las provee el propio módulo.
export const getEmpresasIsp = () => client.get('/isp/empresas').then((r) => r.data)
export const getEstadoIsp = () => client.get('/isp/estado').then((r) => r.data)
export const importarMaestraIsp = (file, empresa) => {
  const fd = new FormData()
  fd.append('file', file)
  return client.post('/isp/maestra/importar', fd, { params: { empresa } }).then((r) => r.data)
}
export const analizarInformeIsp = (file, empresa) => {
  const fd = new FormData()
  fd.append('file', file)
  return client.post('/isp/informe/analizar', fd, { params: { empresa } }).then((r) => r.data)
}
export const generarInformeIsp = (file, empresa) => {
  const fd = new FormData()
  fd.append('file', file)
  return client
    .post('/isp/informe/generar', fd, { params: { empresa }, responseType: 'blob' })
    .then((r) => r.data)
}

// --- Seguimiento Chilexpress (módulo autocontenido) ---
export const getEmpresasChilexpress = () =>
  client.get('/chilexpress/empresas').then((r) => r.data)
export const importarChilexpress = (file, empresa) => {
  const fd = new FormData()
  fd.append('file', file)
  return client.post('/chilexpress/importar', fd, { params: { empresa } }).then((r) => r.data)
}
export const buscarChilexpress = (params) =>
  client.get('/chilexpress/buscar', { params }).then((r) => r.data)
export const getPanelChilexpress = () =>
  client.get('/chilexpress/panel').then((r) => r.data)
export const getClientesChilexpress = () =>
  client.get('/chilexpress/clientes').then((r) => r.data)
