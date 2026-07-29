/**
 * REST API client for the CyberAI C2 server.
 * Handles auth token injection and error normalization.
 */

const BASE_URL = '/api';

let getToken = () => null;

export function setTokenProvider(fn) {
  getToken = fn;
}

async function request(method, path, body = null) {
  const token = getToken();
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };

  const opts = { method, headers };
  if (body) opts.body = JSON.stringify(body);

  const res = await fetch(`${BASE_URL}${path}`, opts);

  if (res.status === 401) {
    // Token expired — clear and redirect
    localStorage.removeItem('cyberai_token');
    localStorage.removeItem('cyberai_user');
    window.location.href = '/login';
    throw new Error('Session expired');
  }

  const data = await res.json();
  if (!res.ok) throw new Error(data.error || `Request failed (${res.status})`);
  return data;
}

// ──── Auth ────
export const login = (username, password) =>
  request('POST', '/auth/login', { username, password });

export const register = (username, password, role) =>
  request('POST', '/auth/register', { username, password, role });

export const getMe = () => request('GET', '/auth/me');

export const changePassword = (currentPassword, newPassword) =>
  request('POST', '/auth/change-password', { currentPassword, newPassword });

// ──── Devices ────
export const getDevices = (params = {}) => {
  const qs = new URLSearchParams();
  if (params.group) qs.set('group', params.group);
  if (params.online) qs.set('online', 'true');
  if (params.search) qs.set('search', params.search);
  const query = qs.toString();
  return request('GET', `/devices${query ? '?' + query : ''}`);
};

export const getDeviceStats = () => request('GET', '/devices/stats');

export const getDevice = (id) => request('GET', `/devices/${id}`);

export const updateDevice = (id, data) => request('PATCH', `/devices/${id}`, data);

export const deleteDevice = (id) => request('DELETE', `/devices/${id}`);

export const sendCommand = (deviceId, type, payload) =>
  request('POST', `/devices/${deviceId}/command`, { type, payload });

export const broadcastCommand = (type, payload, groupId) =>
  request('POST', '/devices/broadcast', { type, payload, groupId });

// ──── Groups ────
export const getGroups = () => request('GET', '/devices/groups/list');

export const createGroup = (name, description) =>
  request('POST', '/devices/groups', { name, description });

export const deleteGroup = (id) => request('DELETE', `/devices/groups/${id}`);

// ──── Builds ────
export const getBuilds = () => request('GET', '/builds');

export const getBuild = (id) => request('GET', `/builds/${id}`);

export const createBuild = (data) => request('POST', '/builds', data);

export const cancelBuild = (id) => request('POST', `/builds/${id}/cancel`);

// ──── Audit ────
export const getAuditLogs = (params = {}) => {
  const qs = new URLSearchParams();
  if (params.limit) qs.set('limit', params.limit);
  if (params.action) qs.set('action', params.action);
  const query = qs.toString();
  return request('GET', `/audit${query ? '?' + query : ''}`);
};

export const getAuditSummary = () => request('GET', '/audit/summary');
