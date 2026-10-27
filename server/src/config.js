'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');

const DEFAULTS = {
  "port": 8080,
  "publicUrl": "http://localhost:8080",
  "owners": ["NameTagged"],
  "devAuth": true,
  "dataDir": "data",
  "downloadUrl": ""
}

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

  const config = { ...DEFAULTS, ...file, ...env };
  config.publicUrl = String(config.publicUrl).replace(/\/+$/, '');
  config.dataDir = path.resolve(ROOT, config.dataDir);
  config.publicDir = path.join(ROOT, 'public');
  config.seedDir = path.join(ROOT, 'seed');
  config.secureCookies = config.publicUrl.startsWith('https://');
  return config;
}

module.exports = { loadConfig, ROOT };
