import { readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');

export function loadConfig() {
  const config = {
    // -------------------------------------------------------- HTTP / WS
    port: parseInt(process.env.PORT || '3000', 10),
    host: process.env.HOST || '0.0.0.0',

    // -------------------------------------------------------- DNS tunnel
    dnsPort: parseInt(process.env.DNS_PORT || '5353', 10),
    dnsDomain: process.env.DNS_DOMAIN || 'c2.cyberai.local',
    dnsSecret: process.env.DNS_SECRET || 'changeme-dns-secret',

    // -------------------------------------------------------- Auth / JWT
    jwtSecret: process.env.JWT_SECRET || 'changeme-jwt-secret-32chars!',
    jwtExpiresIn: process.env.JWT_EXPIRES_IN || '24h',

    // -------------------------------------------------------- Admin account
    adminUsername: process.env.ADMIN_USERNAME || 'admin',
    adminPassword: process.env.ADMIN_PASSWORD || 'admin',

    // -------------------------------------------------------- Database
    dbPath: process.env.DB_PATH || join(ROOT, 'db', 'cyberai.db'),

    // -------------------------------------------------------- Encryption
    encryptionKey: process.env.ENCRYPTION_KEY || 'changeme-enc-key-256bits!!',
    c2Secret: process.env.C2_SECRET || 'c2-shared-secret-change-me',

    // -------------------------------------------------------- GitHub (APK builder)
    githubToken: process.env.GITHUB_TOKEN || '',
    githubRepo: process.env.GITHUB_REPO || '',
    githubWorkflow: process.env.GITHUB_WORKFLOW || 'build-stub.yml',

    // -------------------------------------------------------- Rate limiting
    rateLimitWindow: parseInt(process.env.RATE_LIMIT_WINDOW || '900000', 10),
    rateLimitMax: parseInt(process.env.RATE_LIMIT_MAX || '100', 10),

    // -------------------------------------------------------- Logging
    logLevel: process.env.LOG_LEVEL || 'info',

    // -------------------------------------------------------- CORS
    corsOrigin: process.env.CORS_ORIGIN || '*',

    // -------------------------------------------------------- Payload encryption
    payloadKey: process.env.PAYLOAD_KEY || 'payload-encryption-key--32bytes!',
  };

  // Load channel config
  const channelPath = join(__dirname, 'channels.json');
  if (existsSync(channelPath)) {
    config.channels = JSON.parse(readFileSync(channelPath, 'utf-8'));
  } else {
    config.channels = getDefaultChannels();
  }

  // Validate critical secrets in production
  if (process.env.NODE_ENV === 'production') {
    const checks = [
      ['JWT_SECRET', config.jwtSecret],
      ['ENCRYPTION_KEY', config.encryptionKey],
      ['C2_SECRET', config.c2Secret],
      ['PAYLOAD_KEY', config.payloadKey],
    ];
    for (const [name, val] of checks) {
      if (val.startsWith('changeme')) {
        console.error(`FATAL: ${name} is still set to default value in production`);
        process.exit(1);
      }
    }
  }

  return config;
}

function getDefaultChannels() {
  return {
    primary: { type: 'ws', enabled: true },
    fallback: { type: 'dns', enabled: true, domain: 'c2.cyberai.local' },
    emergency: { type: 'http', enabled: false },
  };
}
