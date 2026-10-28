'use strict';

const { loadConfig } = require('./config');
const { App } = require('./App');

const config = loadConfig();

// devAuth trusts whatever name a client sends, so on a public address anyone could sign in as you.
if (config.devAuth && !/^https?:\/\/(localhost|127\.0\.0\.1|\[::1\])(:\d+)?$/.test(config.publicUrl)) {
  console.error('[astra] Refusing to start: "devAuth" is true but publicUrl is a public address.');
  console.error('[astra] Set "devAuth": false in server/config.json. Anyone could sign in as the Owner otherwise.');
  process.exit(1);
}
const app = new App(config);

/**
 * On Windows another program can hold the same port on IPv4 while this server gets IPv6 (or the
 * other way round). Then the browser reaches Astra but Minecraft reaches the other program.
 */
async function checkPort() {
  for (const host of ['127.0.0.1', '[::1]']) {
    try {
      const res = await fetch(`http://${host}:${config.port}/healthz`, { signal: AbortSignal.timeout(3000) });
      const body = await res.json().catch(() => null);
      if (!body || body.id !== app.instanceId) {
        console.error(`[astra] WARNING: another program is answering on ${host}:${config.port}. Minecraft may reach it instead of Astra.`);
        console.error(`[astra] Change "port" in config.json (and the address in the Astra app's Settings), or close that program.`);
      }
    } catch {
      // Not listening on that address family; fine.
    }
  }
}

app.listen().then(() => {
  console.log(`[astra] Astra server running at ${config.publicUrl} (port ${config.port})`);
  checkPort();
  if (config.devAuth) {
    console.warn('[astra] devAuth is ON: sign-ins are NOT checked with Mojang. Only use this for local testing.');
  }
  if (!config.owners.length) {
    console.warn('[astra] No owners set. Add your Minecraft name to "owners" in config.json to become Owner.');
  }
}).catch((e) => {
  if (e.code === 'EADDRINUSE') {
    console.error(`[astra] Port ${config.port} is already used by another program. Change "port" in config.json.`);
  } else {
    console.error('[astra] Could not start:', e);
  }
  process.exit(1);
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, async () => {
    await app.close();
    process.exit(0);
  });
}
