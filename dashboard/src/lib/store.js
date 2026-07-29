import { create } from 'zustand';

/**
 * Global application state.
 */
export const useStore = create((set, get) => ({
  // ──── Auth ────
  token: localStorage.getItem('cyberai_token') || null,
  user: localStorage.getItem('cyberai_user')
    ? JSON.parse(localStorage.getItem('cyberai_user'))
    : null,

  setAuth: (token, user) => {
    localStorage.setItem('cyberai_token', token);
    localStorage.setItem('cyberai_user', JSON.stringify(user));
    set({ token, user });
  },

  logout: () => {
    localStorage.removeItem('cyberai_token');
    localStorage.removeItem('cyberai_user');
    set({ token: null, user: null });
  },

  // ──── Devices ────
  devices: [],
  devicesLoading: false,
  devicesError: null,

  setDevices: (devices) => set({ devices, devicesLoading: false, devicesError: null }),
  setDevicesLoading: () => set({ devicesLoading: true, devicesError: null }),
  setDevicesError: (error) => set({ devicesError: error, devicesLoading: false }),

  updateDevice: (id, updates) =>
    set(state => ({
      devices: state.devices.map(d => (d.id === id ? { ...d, ...updates } : d)),
    })),

  // ──── Stats ────
  stats: { totalDevices: 0, onlineDevices: 0, totalGroups: 0, commandsToday: 0 },
  setStats: (stats) => set({ stats }),

  // ──── Groups ────
  groups: [],
  setGroups: (groups) => set({ groups }),

  // ──── HVNC ────
  hvncDeviceId: null,
  setHvncDeviceId: (id) => set({ hvncDeviceId: id }),

  // ──── Builds ────
  builds: [],
  setBuilds: (builds) => set({ builds }),

  // ──── Keylogs ────
  keylogs: [],
  setKeylogs: (keylogs) => set({ keylogs }),

  // ──── WebSocket connection status ────
  wsConnected: false,
  setWsConnected: (connected) => set({ wsConnected: connected }),
}));
