const fs = require('fs');
const path = require('path');
const { app } = require('electron');

const DEFAULTS = {
  memoryGb: 3,
  javaPath: '',
  hideOnLaunch: true,
  lastVersion: '1.8.9',
  serverUrl: 'http://localhost:8080'
};

function file() {
  return path.join(app.getPath('userData'), 'settings.json');
}

function get() {
  try {
    const saved = JSON.parse(fs.readFileSync(file(), 'utf8'));
    return { ...DEFAULTS, ...saved };
  } catch {
    return { ...DEFAULTS };
  }
}

function set(patch) {
  const next = { ...get() };
  for (const key of Object.keys(DEFAULTS)) {
    if (patch[key] !== undefined) {
      next[key] = patch[key];
    }
  }
  next.memoryGb = Math.max(1, Math.min(16, Number(next.memoryGb) || DEFAULTS.memoryGb));
  next.javaPath = String(next.javaPath || '').trim();
  next.hideOnLaunch = Boolean(next.hideOnLaunch);
  next.serverUrl = String(next.serverUrl || '').trim().replace(/\/+$/, '');
  if (next.serverUrl && !/^https?:\/\/[^\s/]+/.test(next.serverUrl)) {
    throw new Error('The Astra server address must start with http:// or https://');
  }

  fs.mkdirSync(path.dirname(file()), { recursive: true });
  fs.writeFileSync(file(), JSON.stringify(next, null, 2));
  return next;
}

module.exports = { get, set, DEFAULTS };
