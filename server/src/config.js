'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');

const DEFAULTS = {
  port: 8787,
  // The address players and the launcher use to reach this server (no trailing slash).
  publicUrl: 'http://localhost:8787',
  // Minecraft names or UUIDs that become Owner the first time they sign in.
  owners: [],
  // Skips Mojang's session check so you can test with offline accounts. Never enable on a public server.
  devAuth: false,
  dataDir: 'data',
  // Where the website's Download buttons point (e.g. a GitHub Releases file). Empty = "coming soon".
  downloadUrl: '',
  // Discord (all optional):
  //   discordInvite    an invite link, shown as a Discord button on the website
  //   discordWebhook   a channel webhook URL; new players and staff actions are posted there
  //   discordClientId  your Discord application's ID, for "Playing Astra" status in Discord
  discordInvite: '',
  discordWebhook: '',
  discordClientId: ''
};

/** Reads config.json (if present) and ASTRA_* environment variables. */
function loadConfig() {
  let file = {};
  const configPath = process.env.ASTRA_CONFIG || path.join(ROOT, 'config.json');
  if (fs.existsSync(configPath)) {
    file = JSON.parse(fs.readFileSync(configPath, 'utf8'));
  }
  const env = {};
  if (process.env.PORT) env.port = Number(process.env.PORT);
  if (process.env.ASTRA_PUBLIC_URL) env.publicUrl = process.env.ASTRA_PUBLIC_URL;
  if (process.env.ASTRA_OWNERS) env.owners = process.env.ASTRA_OWNERS.split(',').map((s) => s.trim()).filter(Boolean);
  if (process.env.ASTRA_DEV_AUTH) env.devAuth = process.env.ASTRA_DEV_AUTH === '1';
  if (process.env.ASTRA_DATA_DIR) env.dataDir = process.env.ASTRA_DATA_DIR;
  if (process.env.ASTRA_DOWNLOAD_URL) env.downloadUrl = process.env.ASTRA_DOWNLOAD_URL;
  if (process.env.ASTRA_DISCORD_INVITE) env.discordInvite = process.env.ASTRA_DISCORD_INVITE;
  if (process.env.ASTRA_DISCORD_WEBHOOK) env.discordWebhook = process.env.ASTRA_DISCORD_WEBHOOK;
  if (process.env.ASTRA_DISCORD_CLIENT_ID) env.discordClientId = process.env.ASTRA_DISCORD_CLIENT_ID;

  const config = { ...DEFAULTS, ...file, ...env };
  config.publicUrl = String(config.publicUrl).replace(/\/+$/, '');
  config.dataDir = path.resolve(ROOT, config.dataDir);
  config.publicDir = path.join(ROOT, 'public');
  config.seedDir = path.join(ROOT, 'seed');
  config.secureCookies = config.publicUrl.startsWith('https://');
  return config;
}

module.exports = { loadConfig, ROOT };
