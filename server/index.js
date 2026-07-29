import 'dotenv/config';
import http from 'node:http';
import { createLogger, log } from './utils.js';
import { loadConfig } from './config/index.js';
import { initDatabase } from './db/index.js';
import { createApi } from './api/index.js';
import { createWsServer } from './comms/wsServer.js';
import { createDnsServer } from './comms/dnsServer.js';
import { createChannelMux } from './comms/channelMux.js';

async function main() {
  const config = loadConfig();
  createLogger(config.logLevel);

  log('info', 'Starting CyberAI C2 server', { version: '1.0.0' });

  // ------------------------------------------------------------------ Database
  const db = initDatabase(config);
  log('info', 'Database initialized', { path: config.dbPath });

  // ------------------------------------------------------------- HTTP + API
  const app = express();
  const httpServer = http.createServer(app);

  app.disable('x-powered-by');

  const apiRouter = await createApi(db, config);
  app.use('/api', apiRouter);

  // Health check (no auth)
  app.get('/health', (_req, res) => res.json({ status: 'ok', uptime: process.uptime() }));

  // ----------------------------------------------------------- C2 Channels
  const wsServer = createWsServer(httpServer, db, config);
  const dnsServer = createDnsServer(db, config);
  const channelMux = createChannelMux(wsServer, dnsServer, db, config);

  log('info', 'C2 channels initialized', { ws: true, dns: true });

  // -------------------------------------------------------- Graceful shutdown
  function shutdown(signal) {
    log('info', `Received ${signal}, shutting down gracefully...`);
    dnsServer.close();
    wsServer.close();
    httpServer.close(() => {
      db.close();
      log('info', 'Shutdown complete');
      process.exit(0);
    });
    // Force exit after 5s
    setTimeout(() => process.exit(1), 5000);
  }

  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));

  // ---------------------------------------------------------------- Listen
  const port = config.port;
  httpServer.listen(port, () => {
    log('info', `HTTP server listening on port ${port}`);
    log('info', `WebSocket ready on ws://0.0.0.0:${port}`);
    log('info', `DNS tunnel on udp://0.0.0.0:${config.dnsPort}`);
  });
}

main().catch(err => {
  console.error('FATAL:', err);
  process.exit(1);
});
