import { useState } from 'react';

export default function KeylogViewer({ keylogs = [] }) {
  const [filter, setFilter] = useState('');

  const filtered = filter
    ? keylogs.filter(
        k =>
          (k.package_name || '').toLowerCase().includes(filter.toLowerCase()) ||
          (k.text || '').toLowerCase().includes(filter.toLowerCase())
      )
    : keylogs;

  return (
    <div className="space-y-3">
      {/* Filter */}
      <div className="flex items-center gap-2">
        <input
          type="text"
          value={filter}
          onChange={e => setFilter(e.target.value)}
          placeholder="Filter by app or text..."
          className="flex-1 h-8 px-3 text-xs bg-surface-800 border border-surface-700 rounded text-gray-200 placeholder-surface-500 outline-none focus:border-accent/50 transition-colors"
        />
        <span className="text-xs text-surface-500 font-mono">
          {filtered.length} / {keylogs.length}
        </span>
      </div>

      {/* Log entries */}
      <div className="space-y-1 max-h-96 overflow-y-auto">
        {filtered.length === 0 && (
          <p className="text-xs text-surface-500 text-center py-8">No keylog entries</p>
        )}
        {filtered.map((entry, i) => (
          <div
            key={entry.id || i}
            className="flex items-start gap-3 p-2 bg-surface-900/50 rounded text-xs font-mono animate-slide-in"
            style={{ animationDelay: `${i * 20}ms` }}
          >
            <span className="text-surface-500 whitespace-nowrap">
              {new Date(entry.captured_at || entry.received_at).toLocaleTimeString()}
            </span>
            <span className="text-accent whitespace-nowrap">
              {entry.package_name || 'system'}
            </span>
            <span className="text-gray-300 break-all flex-1">
              {entry.text || entry.key_event || '(empty)'}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
