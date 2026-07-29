import { Router } from 'express';
import { createAuthRouter } from './auth.js';
import { createDevicesRouter } from './devices.js';
import { createBuildsRouter } from './builds.js';
import { createAuditRouter } from './audit.js';
import cors from 'cors';
import rateLimit from 'express-rate-limit';

export async function createApi(db, config) {
  const router = Router();

  // ----------------------------------------------------------------- CORS
  router.use(cors({ origin: config.corsOrigin, credentials: true }));

  // ----------------------------------------------------------- Rate limiting
  const limiter = rateLimit({
    windowMs: config.rateLimitWindow,
    max: config.rateLimitMax,
    standardHeaders: true,
    legacyHeaders: false,
    message: { error: 'Too many requests, slow down.' },
  });
  router.use(limiter);

  // ------------------------------------------------------------- Body parsing
  router.use(express.json({ limit: '10mb' }));
  router.use(express.urlencoded({ extended: true }));

  // ---------------------------------------------------------------- Mount routes
  router.use('/auth', createAuthRouter(db, config));
  router.use('/devices', createDevicesRouter(db, config));
  router.use('/builds', createBuildsRouter(db, config));
  router.use('/audit', createAuditRouter(db, config));

  // -------------------------------------------------------- 404 catch-all
  router.use((_req, res) => {
    res.status(404).json({ error: 'Route not found' });
  });

  // -------------------------------------------------------- Error handler
  router.use((err, _req, res, _next) => {
    log('error', 'API error', { message: err.message, stack: err.stack?.split('\n')[0] });
    res.status(err.status || 500).json({
      error: err.message || 'Internal server error',
    });
  });

  return router;
}
