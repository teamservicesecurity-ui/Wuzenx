import { useState } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useStore } from '../lib/store.js';
import { useWS } from '../hooks/useWS.js';

const navItems = [
  { path: '/', label: 'Dashboard', icon: '⊞' },
  { path: '/devices', label: 'Devices', icon: '📱' },
  { path: '/hvnc', label: 'HVNC', icon: '🖥' },
  { path: '/builder', label: 'Builder', icon: '⚙' },
  { path: '/logs', label: 'Logs', icon: '📋' },
];

export default function Layout() {
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const { user, logout, wsConnected } = useStore();
  const navigate = useNavigate();
  useWS();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="flex h-screen overflow-hidden">
      {/* ──── Sidebar ──── */}
      <aside
        className={`${
          sidebarOpen ? 'w-56' : 'w-16'
        } flex-shrink-0 bg-surface-900 border-r border-surface-800 transition-all duration-200 flex flex-col`}
      >
        {/* Logo */}
        <div className="h-14 flex items-center px-4 border-b border-surface-800">
          <span className="text-accent font-bold text-lg tracking-wider">
            {sidebarOpen ? 'CYBERAI' : 'C'}
          </span>
          {sidebarOpen && (
            <span className="text-surface-400 text-xs ml-2">v1.0</span>
          )}
        </div>

        {/* Navigation */}
        <nav className="flex-1 py-2 space-y-0.5">
          {navItems.map(item => (
            <NavLink
              key={item.path}
              to={item.path}
              end={item.path === '/'}
              className={({ isActive }) =>
                `flex items-center h-10 px-4 text-sm transition-colors ${
                  isActive
                    ? 'bg-accent/10 text-accent border-r-2 border-accent'
                    : 'text-surface-400 hover:text-gray-100 hover:bg-surface-800'
                }`
              }
            >
              <span className="text-base w-6 text-center flex-shrink-0">{item.icon}</span>
              {sidebarOpen && <span className="ml-3">{item.label}</span>}
            </NavLink>
          ))}
        </nav>

        {/* Bottom section */}
        <div className="border-t border-surface-800 p-3 space-y-2">
          {sidebarOpen && user && (
            <div className="text-xs text-surface-400 truncate px-2">
              {user.username}
              <span className="ml-2 text-accent">● {user.role}</span>
            </div>
          )}
          <button
            onClick={handleLogout}
            className="w-full flex items-center h-8 px-2 text-sm text-danger hover:bg-danger/10 rounded transition-colors"
          >
            <span className="text-base w-6 text-center flex-shrink-0">⏻</span>
            {sidebarOpen && <span className="ml-3">Logout</span>}
          </button>
          <button
            onClick={() => setSidebarOpen(!sidebarOpen)}
            className="w-full flex items-center h-8 px-2 text-sm text-surface-400 hover:text-gray-100 hover:bg-surface-800 rounded transition-colors"
          >
            <span className="text-xs w-6 text-center flex-shrink-0">{sidebarOpen ? '◀' : '▶'}</span>
            {sidebarOpen && <span className="ml-3">Collapse</span>}
          </button>
        </div>
      </aside>

      {/* ──── Main Content ──── */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Top bar */}
        <header className="h-14 flex-shrink-0 bg-surface-950 border-b border-surface-800 flex items-center justify-between px-6">
          <div className="flex items-center gap-4">
            {/* WS Status */}
            <div className="flex items-center gap-2 text-xs">
              <span
                className={`status-dot ${wsConnected ? 'online animate-pulse-dot' : 'offline'}`}
              />
              <span className={wsConnected ? 'text-success' : 'text-surface-400'}>
                {wsConnected ? 'Connected' : 'Disconnected'}
              </span>
            </div>
          </div>
          <div className="flex items-center gap-3 text-xs text-surface-400">
            <span>UTC {new Date().toLocaleTimeString('en-US', { timeZone: 'UTC', hour12: false })}</span>
            <span className="w-px h-4 bg-surface-700" />
            <span>{user?.username || 'operator'}</span>
          </div>
        </header>

        {/* Page content */}
        <main className="flex-1 overflow-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
