const fs = require('fs');
const path = require('path');
const { app } = require('electron');

/**
 * Writes launcher + game output to %APPDATA%/Astra Launcher/logs/latest.log
 * (the previous run is kept as previous.log), so problems can be diagnosed after the fact.
 */
let stream = null;

function dir() {
  return path.join(app.getPath('userData'), 'logs');
}

function open() {
  if (stream) return;
  fs.mkdirSync(dir(), { recursive: true });
  const latest = path.join(dir(), 'latest.log');
  try {
    if (fs.existsSync(latest)) fs.renameSync(latest, path.join(dir(), 'previous.log'));
  } catch {
    // another instance may hold it; just append
  }
  stream = fs.createWriteStream(latest, { flags: 'a' });
  write(`Astra Launcher ${app.getVersion()} on ${process.platform} ${process.arch}, Electron ${process.versions.electron}`);
}

function write(line) {
  if (!stream) open();
  stream.write(`[${new Date().toISOString()}] ${line}\n`);
}

module.exports = { open, write, dir };
