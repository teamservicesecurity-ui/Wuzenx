import { useState, useEffect } from 'react';

const STEPS = ['queued', 'running', 'success'];
const STEP_LABELS = { queued: 'Queued', running: 'Building', success: 'Complete', failed: 'Failed' };

export default function BuildProgress({ build }) {
  const [elapsed, setElapsed] = useState(0);

  useEffect(() => {
    if (!build || build.status === 'success' || build.status === 'failed') return;
    const start = new Date(build.created_at).getTime();
    const timer = setInterval(() => {
      setElapsed(Math.floor((Date.now() - start) / 1000));
    }, 1000);
    return () => clearInterval(timer);
  }, [build]);

  if (!build) {
    return (
      <div className="bg-surface-900 rounded-lg border border-surface-800 p-6 text-center">
        <p className="text-sm text-surface-400">No builds yet</p>
        <p className="text-xs text-surface-500 mt-1">Configure and submit the form to start</p>
      </div>
    );
  }

  const currentStep = STEPS.indexOf(build.status);
  const isFailed = build.status === 'failed';

  return (
    <div className="bg-surface-900 rounded-lg border border-surface-800 p-5 space-y-4">
      {/* Status header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          {isFailed ? (
            <span className="text-danger">✕</span>
          ) : build.status === 'success' ? (
            <span className="text-success">✓</span>
          ) : (
            <span className="w-2 h-2 bg-warning rounded-full animate-pulse" />
          )}
          <span className={`text-sm font-medium ${
            isFailed ? 'text-danger' : build.status === 'success' ? 'text-success' : 'text-warning'
          }`}>
            {STEP_LABELS[build.status] || build.status}
          </span>
        </div>
        <span className="text-xs text-surface-500 font-mono">
          {build.status === 'success' || build.status === 'failed'
            ? ''
            : `${elapsed}s`}
        </span>
      </div>

      {/* Build info */}
      <div className="grid grid-cols-2 gap-2 text-xs">
        <div>
          <span className="text-surface-500">Build ID</span>
          <p className="text-gray-300 font-mono">{build.id?.slice(0, 16)}...</p>
        </div>
        <div>
          <span className="text-surface-500">Package</span>
          <p className="text-gray-300 font-mono text-[11px]">{build.package_name || '—'}</p>
        </div>
        <div>
          <span className="text-surface-500">Config</span>
          <p className="text-gray-300">{build.build_config || 'release'}</p>
        </div>
        <div>
          <span className="text-surface-500">Created</span>
          <p className="text-gray-300">
            {build.created_at ? new Date(build.created_at).toLocaleString() : '—'}
          </p>
        </div>
      </div>

      {/* Progress bar */}
      {!isFailed && build.status !== 'success' && (
        <div className="w-full h-1.5 bg-surface-800 rounded-full overflow-hidden">
          <div
            className="h-full bg-accent rounded-full transition-all duration-500"
            style={{ width: `${currentStep >= 0 ? ((currentStep + 1) / STEPS.length) * 100 : 0}%` }}
          />
        </div>
      )}

      {/* Error */}
      {build.error && (
        <div className="p-2 bg-danger/10 border border-danger/20 rounded text-xs text-danger font-mono">
          {build.error}
        </div>
      )}

      {/* Download button */}
      {build.status === 'success' && build.apk_url && (
        <a
          href={build.apk_url}
          download
          className="block w-full h-9 leading-9 text-center text-sm bg-success hover:bg-success/80 text-white rounded transition-colors"
        >
          ⬇ Download APK
        </a>
      )}
    </div>
  );
}
