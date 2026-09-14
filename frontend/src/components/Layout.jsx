import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import {
  IconDashboard,
  IconStock,
  IconAssign,
  IconSearch,
  IconClients,
  IconUser,
  IconCopy,
  IconUpload,
  IconBox,
  IconMenu,
  IconLogout,
} from './icons'

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', icon: IconDashboard, roles: ['ADMIN'] },
  { to: '/stock', label: 'Stock', icon: IconStock, roles: ['ADMIN', 'OPERADOR'] },
  { to: '/armar', label: 'Armar', icon: IconBox, roles: ['ADMIN', 'OPERADOR'] },
  { to: '/asignar', label: 'Asignar', icon: IconAssign, roles: ['ADMIN', 'OPERADOR'] },
  { to: '/dosimetros', label: 'Dosímetros', icon: IconSearch, roles: ['ADMIN', 'OPERADOR'] },
  { to: '/mis-dosimetros', label: 'Asignaciones por cliente', icon: IconAssign, roles: ['ADMIN', 'OPERADOR'] },
  { to: '/pendiente-asignacion', label: 'Pendiente de asignación', icon: IconAssign, roles: ['ADMIN', 'OPERADOR', 'EJECUTIVO'] },
  { to: '/clientes', label: 'Clientes', icon: IconClients, roles: ['ADMIN', 'OPERADOR'] },
  { to: '/duplicados', label: 'Duplicados', icon: IconCopy, roles: ['ADMIN', 'OPERADOR'] },
  { to: '/tipos-porta', label: 'Tipos de porta', icon: IconStock, roles: ['ADMIN', 'OPERADOR'] },
  { to: '/importar', label: 'Importar Excel', icon: IconUpload, roles: ['ADMIN'] },
  { to: '/informe-isp', label: 'Informe ISP', icon: IconUpload, roles: ['ADMIN'] },
  { to: '/seguimiento', label: 'Seguimiento Chilexpress', icon: IconSearch, roles: ['ADMIN', 'OPERADOR', 'EJECUTIVO'] },
  { to: '/usuarios-ejecutivos', label: 'Usuarios y ejecutivos', icon: IconUser, roles: ['ADMIN', 'OPERADOR'] },
  { to: '/mis-dosimetros', label: 'Mis dosímetros', icon: IconBox, roles: ['EJECUTIVO'] },
  { to: '/mis-clientes', label: 'Mis clientes', icon: IconClients, roles: ['EJECUTIVO'] },
  { to: '/comparador', label: 'Comparador', icon: IconCopy, roles: ['EJECUTIVO'] },
]

export default function Layout() {
  const { username, rol, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  const items = NAV_ITEMS.filter((item) => item.roles.includes(rol))

  const sidebar = (
    <div className="h-full flex flex-col">
      <div className="px-5 py-5 flex items-center gap-2.5">
        <div className="w-9 h-9 rounded-lg bg-steel flex items-center justify-center text-white">
          <IconBox />
        </div>
        <div>
          <h1 className="text-sm font-bold text-white leading-tight">Dosímetros</h1>
          <p className="text-[11px] text-white/50">Gestión de stock</p>
        </div>
      </div>
      <nav className="flex-1 px-3 py-2 space-y-0.5 overflow-y-auto">
        {items.map((item) => {
          const Icon = item.icon
          return (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              onClick={() => setOpen(false)}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition ${
                  isActive ? 'bg-steel text-white' : 'text-white/70 hover:bg-white/10'
                }`
              }
            >
              <Icon />
              {item.label}
            </NavLink>
          )
        })}
      </nav>
      <div className="px-3 py-4 border-t border-white/10">
        <div className="px-2 mb-3">
          <p className="text-sm text-white font-medium truncate">{username}</p>
          <p className="text-[11px] text-white/50">{rol}</p>
        </div>
        <button
          onClick={handleLogout}
          className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm text-white/70 hover:bg-white/10 transition"
        >
          <IconLogout />
          Cerrar sesión
        </button>
      </div>
    </div>
  )

  return (
    <div className="min-h-full">
      {/* Sidebar fijo en escritorio */}
      <aside className="hidden lg:block fixed inset-y-0 left-0 w-64 bg-ink">{sidebar}</aside>

      {/* Drawer móvil */}
      {open && (
        <div className="lg:hidden fixed inset-0 z-40">
          <div className="absolute inset-0 bg-black/40" onClick={() => setOpen(false)} />
          <aside className="absolute inset-y-0 left-0 w-64 bg-ink shadow-xl">{sidebar}</aside>
        </div>
      )}

      <div className="lg:pl-64 flex flex-col min-h-screen">
        {/* Top bar móvil */}
        <header className="lg:hidden sticky top-0 z-30 bg-white border-b border-mist px-4 py-3 flex items-center gap-3">
          <button
            onClick={() => setOpen(true)}
            className="text-ink p-1"
            aria-label="Abrir menú"
          >
            <IconMenu width={22} height={22} />
          </button>
          <span className="font-semibold text-ink">Dosímetros</span>
        </header>

        <main className="flex-1 p-4 sm:p-6 lg:p-8 max-w-7xl w-full mx-auto">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
