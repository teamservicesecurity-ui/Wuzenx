import { useState } from 'react';

export default function BuildForm({ onSubmit, loading }) {
  const [form, setForm] = useState({
    packageName: '',
    iconUrl: '',
    buildConfig: 'release',
    c2Url: window.location.origin,
    encryptionKey: '',
  });

  const handleChange = (field) => (e) => {
    setForm(prev => ({ ...prev, [field]: e.target.value }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onSubmit(form);
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {/* C2 URL */}
      <div>
        <label className="block text-xs text-surface-400 mb-1 font-mono uppercase tracking-wider">
          C2 Server URL
        </label>
        <input
          type="text"
          value={form.c2Url}
          onChange={handleChange('c2Url')}
          required
          placeholder="https://your-server.com"
          className="w-full h-9 px-3 text-sm bg-surface-800 border border-surface-700 rounded text-gray-200 placeholder-surface-500 outline-none focus:border-accent/50 transition-colors font-mono"
        />
      </div>

      {/* Package Name */}
      <div>
        <label className="block text-xs text-surface-400 mb-1 font-mono uppercase tracking-wider">
          Package Name
          <span className="text-surface-500 ml-1">(leave empty for random)</span>
        </label>
        <input
          type="text"
          value={form.packageName}
          onChange={handleChange('packageName')}
          placeholder="com.android.system.update"
          className="w-full h-9 px-3 text-sm bg-surface-800 border border-surface-700 rounded text-gray-200 placeholder-surface-500 outline-none focus:border-accent/50 transition-colors font-mono"
        />
      </div>

      {/* Build Config */}
      <div>
        <label className="block text-xs text-surface-400 mb-1 font-mono uppercase tracking-wider">
          Build Configuration
        </label>
        <select
          value={form.buildConfig}
          onChange={handleChange('buildConfig')}
          className="w-full h-9 px-3 text-sm bg-surface-800 border border-surface-700 rounded text-gray-200 outline-none focus:border-accent/50 transition-colors"
        >
          <option value="debug">Debug</option>
          <option value="release">Release</option>
          <option value="custom">Custom</option>
        </select>
      </div>

      {/* Icon URL */}
      <div>
        <label className="block text-xs text-surface-400 mb-1 font-mono uppercase tracking-wider">
          Icon URL
          <span className="text-surface-500 ml-1">(optional)</span>
        </label>
        <input
          type="text"
          value={form.iconUrl}
          onChange={handleChange('iconUrl')}
          placeholder="https://example.com/icon.png"
          className="w-full h-9 px-3 text-sm bg-surface-800 border border-surface-700 rounded text-gray-200 placeholder-surface-500 outline-none focus:border-accent/50 transition-colors font-mono"
        />
      </div>

      {/* Encryption Key */}
      <div>
        <label className="block text-xs text-surface-400 mb-1 font-mono uppercase tracking-wider">
          Encryption Key
          <span className="text-surface-500 ml-1">(leave empty for auto-generate)</span>
        </label>
        <input
          type="text"
          value={form.encryptionKey}
          onChange={handleChange('encryptionKey')}
          placeholder="Auto-generate"
          className="w-full h-9 px-3 text-sm bg-surface-800 border border-surface-700 rounded text-gray-200 placeholder-surface-500 outline-none focus:border-accent/50 transition-colors font-mono"
        />
      </div>

      {/* Submit */}
      <button
        type="submit"
        disabled={loading}
        className="w-full h-10 text-sm font-medium bg-accent hover:bg-accent-dark text-white rounded disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
      >
        {loading ? 'Building...' : 'Build APK'}
      </button>
    </form>
  );
}
