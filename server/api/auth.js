import { Router } from 'express';
import bcrypt from 'bcryptjs';
import { v4 as uuidv4 } from 'uuid';
import { signJwt, verifyJwt, log } from '../utils.js';

// Authentication middleware — exported for reuse
export function authMiddleware(db, config) {
  return (req, res, next) => {
    const header = req.headers.authorization;
    if (!header || !header.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'Missing or invalid authorization header' });
    }
    const token = header.slice(7);
    try {
      const payload = verifyJwt(token, config.jwtSecret);
      const user = db.stmts.getUserById.get(payload.sub);
      if (!user) {
        return res.status(401).json({ error: 'User not found' });
      }
      req.user = { id: user.id, username: user.username, role: user.role };
      next();
    } catch (err) {
      if (err.name === 'TokenExpiredError') {
        return res.status(401).json({ error: 'Token expired', code: 'TOKEN_EXPIRED' });
      }
      return res.status(401).json({ error: 'Invalid token' });
    }
  };
}

// Admin-only middleware
export function adminMiddleware(_req, res, next) {
  if (_req.user?.role !== 'admin') {
    return res.status(403).json({ error: 'Admin access required' });
  }
  next();
}

export function createAuthRouter(db, config) {
  const router = Router();
  const requireAuth = authMiddleware(db, config);

  // POST /api/auth/login
  router.post('/login', (req, res) => {
    const { username, password } = req.body;
    if (!username || !password) {
      return res.status(400).json({ error: 'Username and password required' });
    }

    const user = db.stmts.getUserByUsername.get(username);
    if (!user) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    const valid = bcrypt.compareSync(password, user.password_hash);
    if (!valid) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    // Update last login
    db.stmts.updateUserLogin.run(new Date().toISOString(), user.id);

    // Audit
    db.stmts.insertAudit.run({
      id: uuidv4(),
      userId: user.id,
      action: 'login',
      targetType: 'user',
      targetId: user.id,
      details: JSON.stringify({ username }),
      ip: req.ip,
    });

    const token = signJwt(
      { sub: user.id, username: user.username, role: user.role },
      config.jwtSecret,
      config.jwtExpiresIn
    );

    log('info', 'User logged in', { username: user.username, role: user.role });

    res.json({
      token,
      user: { id: user.id, username: user.username, role: user.role },
    });
  });

  // POST /api/auth/register (admin only, creates new operators)
  router.post('/register', requireAuth, (req, res) => {
    if (req.user.role !== 'admin') {
      return res.status(403).json({ error: 'Only admins can create users' });
    }

    const { username, password, role } = req.body;
    if (!username || !password) {
      return res.status(400).json({ error: 'Username and password required' });
    }

    const existing = db.stmts.getUserByUsername.get(username);
    if (existing) {
      return res.status(409).json({ error: 'Username already exists' });
    }

    const hash = bcrypt.hashSync(password, 12);
    const id = uuidv4();

    db.stmts.createUser.run({
      id,
      username,
      passwordHash: hash,
      role: role || 'operator',
    });

    log('info', 'User created', { username, role: role || 'operator', by: req.user.username });

    res.status(201).json({ id, username, role: role || 'operator' });
  });

  // GET /api/auth/me
  router.get('/me', requireAuth, (req, res) => {
    res.json({ user: req.user });
  });

  // POST /api/auth/change-password
  router.post('/change-password', requireAuth, (req, res) => {
    const { currentPassword, newPassword } = req.body;
    if (!currentPassword || !newPassword) {
      return res.status(400).json({ error: 'Current and new password required' });
    }

    const user = db.stmts.getUserById.get(req.user.id);
    if (!bcrypt.compareSync(currentPassword, user.password_hash)) {
      return res.status(401).json({ error: 'Current password is incorrect' });
    }

    const hash = bcrypt.hashSync(newPassword, 12);
    db.db.prepare('UPDATE users SET password_hash = ? WHERE id = ?').run(hash, user.id);

    log('info', 'Password changed', { username: user.username });

    res.json({ message: 'Password updated' });
  });

  return router;
}
