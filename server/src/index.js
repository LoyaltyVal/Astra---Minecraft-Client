'use strict';

const { loadConfig } = require('./config');
const { App } = require('./App');

const config = loadConfig();
const app = new App(config);

app.listen().then(() => {
  console.log(`[astra] Astra server running at ${config.publicUrl} (port ${config.port})`);
  if (config.devAuth) {
    console.warn('[astra] devAuth is ON: sign-ins are NOT checked with Mojang. Only use this for local testing.');
  }
  if (!config.owners.length) {
    console.warn('[astra] No owners set. Add your Minecraft name to "owners" in config.json to become Owner.');
  }
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, async () => {
    await app.close();
    process.exit(0);
  });
}
