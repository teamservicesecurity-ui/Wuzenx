import { Router } from 'express';
import { authMiddleware } from './auth.js';

export function createAuditRouter(db, config) {
  const router = Router();
  const requireAuth = authMiddleware(db, config);

  router.use(requireAuth);

  // GET /api/audit — query audit logs
  router.get('/', (req, res) => {
    const limit = Math.min(parseInt(req.query.limit) || 100, 1000);
    const action = req.query.action;
    const userId = req.query.userId;

    let logs;
    if (action && userId) {
      logs = db.db.prepare(
        'SELECT * FROM audit_logs WHERE action = ? AND user_id = ? ORDER BY created_at DESC LIMIT ?'
      ).all(action, userId, limit);
    } else if (action) {
      logs = db.db.prepare(
        'SELECT * FROM audit_logs WHERE action = ? ORDER BY created_at DESC LIMIT ?'
      ).all(action, limit);
    } else if (userId) {
      logs = db.db.prepare(
        'SELECT * FROM audit_logs WHERE user_id = ? ORDER BY created_at DESC LIMIT ?'
      ).all(userId, limit);
    } else {
      logs = db.stmts.getAuditLogs.all(limit);
    }

    res.json({ logs, count: logs.length });
  });

  // GET /api/audit/summary — action counts
  router.get('/summary', (req, res) => {
    const summary = db.db.prepare(`
      SELECT action, COUNT(*) as count FROM audit_logs 
      WHERE date(created_at) = date('now') 
      GROUP BY action ORDER BY count DESC
    `).all();

    res.json({ summary });
  });

  return router;
}
