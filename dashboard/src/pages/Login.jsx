import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useStore } from '../lib/store.js';
import { login as apiLogin } from '../lib/api.js';
import { setTokenProvider } from '../lib/api.js';

export default function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const setAuth = useStore(s => s.setAuth);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const data = await apiLogin(username, password);
      setAuth(data.token, data.user);
      setTokenProvider(() => data.token);
      navigate('/', { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-surface-950 flex items-center justify-center p-4">
      <div className="w-full max-w-sm">
        {/* Logo */}
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-accent tracking-widest">CYBERAI</h1>
          <p className="text-xs text-surface-500 mt-2 font-mono">C2 Control Panel v1.0</p>
        </div>

        {/* Form */}
        <form
          onSubmit={handleSubmit}
          className="bg-surface-900 border border-surface-800 rounded-lg p-6 space-y-4"
        >
          <div>
            <label className="block text-xs text-surface-400 mb-1.5 font-mono uppercase tracking-wider">
              Username
            </label>
            <input
              type="text"
              value={username}
              onChange={e => setUsername(e.target.value)}
              autoFocus
              required
              className="w-full h-10 px-3 text-sm bg-surface-800 border border-surface-700 rounded text-gray-200 placeholder-surface-500 outline-none focus:border-accent/50 transition-colors"
              placeholder="admin"
            />
          </div>

          <div>
            <label className="block text-xs text-surface-400 mb-1.5 font-mono uppercase tracking-wider">
              Password
            </label>
            <input
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              required
              className="w-full h-10 px-3 text-sm bg-surface-800 border border-surface-700 rounded text-gray-200 placeholder-surface-500 outline-none focus:border-accent/50 transition-colors"
              placeholder="••••••••"
            />
          </div>

          {error && (
            <div className="p-2 bg-danger/10 border border-danger/20 rounded text-xs text-danger text-center">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full h-10 text-sm font-medium bg-accent hover:bg-accent-dark text-white rounded disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          >
            {loading ? 'Authenticating...' : 'Sign In'}
          </button>
        </form>

        <p className="text-[10px] text-surface-600 text-center mt-4 font-mono">
          Authorized operators only
        </p>
      </div>
    </div>
  );
}
