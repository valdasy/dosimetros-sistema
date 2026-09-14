import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import ProtectedRoute from './auth/ProtectedRoute'
import Layout from './components/Layout'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Stock from './pages/Stock'
import Asignar from './pages/Asignar'
import Armar from './pages/Armar'
import Dosimetros from './pages/Dosimetros'
import Clientes from './pages/Clientes'
import Duplicados from './pages/Duplicados'
import TiposPorta from './pages/TiposPorta'
import Importar from './pages/Importar'
import UsuariosEjecutivos from './pages/UsuariosEjecutivos'
import MisDosimetros from './pages/MisDosimetros'
import MisClientes from './pages/MisClientes'
import Comparador from './pages/Comparador'
import PendienteAsignacion from './pages/PendienteAsignacion'
import InformeIsp from './pages/InformeIsp'
import Seguimiento from './pages/Seguimiento'

// Redirige a la pantalla inicial según el rol.
function Home() {
  const { rol } = useAuth()
  if (rol === 'EJECUTIVO') return <Navigate to="/mis-dosimetros" replace />
  if (rol === 'OPERADOR') return <Navigate to="/stock" replace />
  return <Dashboard />
}

const OPER = ['ADMIN', 'OPERADOR']

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />

      <Route
        element={
          <ProtectedRoute>
            <Layout />
          </ProtectedRoute>
        }
      >
        <Route path="/" element={<Home />} />
        <Route path="/stock" element={<ProtectedRoute roles={OPER}><Stock /></ProtectedRoute>} />
        <Route path="/armar" element={<ProtectedRoute roles={OPER}><Armar /></ProtectedRoute>} />
        <Route path="/asignar" element={<ProtectedRoute roles={OPER}><Asignar /></ProtectedRoute>} />
        <Route path="/dosimetros" element={<ProtectedRoute roles={OPER}><Dosimetros /></ProtectedRoute>} />
        <Route
          path="/pendiente-asignacion"
          element={<ProtectedRoute roles={['ADMIN', 'OPERADOR', 'EJECUTIVO']}><PendienteAsignacion /></ProtectedRoute>}
        />
        <Route path="/pendiente-envio" element={<Navigate to="/pendiente-asignacion" replace />} />
        <Route path="/buscar" element={<Navigate to="/dosimetros" replace />} />
        <Route path="/clientes" element={<ProtectedRoute roles={OPER}><Clientes /></ProtectedRoute>} />
        <Route path="/duplicados" element={<ProtectedRoute roles={OPER}><Duplicados /></ProtectedRoute>} />
        <Route path="/tipos-porta" element={<ProtectedRoute roles={OPER}><TiposPorta /></ProtectedRoute>} />
        <Route path="/importar" element={<ProtectedRoute roles={['ADMIN']}><Importar /></ProtectedRoute>} />
        <Route path="/informe-isp" element={<ProtectedRoute roles={['ADMIN']}><InformeIsp /></ProtectedRoute>} />
        <Route path="/seguimiento" element={<ProtectedRoute roles={['ADMIN', 'OPERADOR', 'EJECUTIVO']}><Seguimiento /></ProtectedRoute>} />
        {/* Módulo unificado: usuarios de acceso (ADMIN) + ejecutivos (ADMIN/OPERADOR) */}
        <Route path="/usuarios-ejecutivos" element={<ProtectedRoute roles={OPER}><UsuariosEjecutivos /></ProtectedRoute>} />
        {/* Compatibilidad con rutas antiguas */}
        <Route path="/ejecutivos" element={<Navigate to="/usuarios-ejecutivos" replace />} />
        <Route path="/usuarios" element={<Navigate to="/usuarios-ejecutivos?tab=usuarios" replace />} />
        <Route
          path="/mis-dosimetros"
          element={<ProtectedRoute roles={['ADMIN', 'OPERADOR', 'EJECUTIVO']}><MisDosimetros /></ProtectedRoute>}
        />
        {/* "Mis asignaciones" se fusionó en "Mis dosímetros" */}
        <Route path="/mis-asignaciones" element={<Navigate to="/mis-dosimetros" replace />} />
        <Route
          path="/mis-clientes"
          element={<ProtectedRoute roles={['EJECUTIVO']}><MisClientes /></ProtectedRoute>}
        />
        <Route
          path="/comparador"
          element={<ProtectedRoute roles={['EJECUTIVO']}><Comparador /></ProtectedRoute>}
        />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
