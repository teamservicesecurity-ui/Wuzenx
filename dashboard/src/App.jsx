import { Routes, Route, Navigate } from 'react-router-dom';
import { useStore } from './lib/store.js';
import Layout from './components/Layout.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Devices from './pages/Devices.jsx';
import DeviceDetail from './pages/DeviceDetail.jsx';
import HVNC from './pages/HVNC.jsx';
import Builder from './pages/Builder.jsx';
import Logs from './pages/Logs.jsx';
import Login from './pages/Login.jsx';

function ProtectedRoute({ children }) {
  const token = useStore(s => s.token);
  if (!token) return <Navigate to="/login" replace />;
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <Layout />
          </ProtectedRoute>
        }
      >
        <Route index element={<Dashboard />} />
        <Route path="devices" element={<Devices />} />
        <Route path="devices/:id" element={<DeviceDetail />} />
        <Route path="hvnc" element={<HVNC />} />
        <Route path="builder" element={<Builder />} />
        <Route path="logs" element={<Logs />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
