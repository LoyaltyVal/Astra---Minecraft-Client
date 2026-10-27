#!/usr/bin/env node
'use strict';

/**
 * One script to run Astra. Double-click "Start Astra.bat" (or run `node tools/astra.js`).
 *
 *   node tools/astra.js            set up if needed, build, start the server, open the app
 *   node tools/astra.js build      build the Minecraft client jars only
 *   node tools/astra.js server     start the server only (in this window)
 *   node tools/astra.js installer  build a Windows installer for players (launcher/dist)
 */

const fs = require('fs');
const path = require('path');
const readline = require('readline');
const { spawn, spawnSync } = require('child_process');

const ROOT = path.join(__dirname, '..');
const SERVER = path.join(ROOT, 'server');
const LAUNCHER = path.join(ROOT, 'launcher');
const WIN = process.platform === 'win32';

const say = (msg) => console.log(`\x1b[35m[Astra]\x1b[0m ${msg}`);
const fail = (msg) => {
  console.error(`\x1b[31m[Astra] ${msg}\x1b[0m`);
  process.exit(1);
};

function run(cmd, args, cwd, label) {
  say(label);
  const r = spawnSync(cmd, args, { cwd, stdio: 'inherit', shell: WIN });
  if (r.status !== 0) fail(`${label} failed. Scroll up for the error.`);
}

function ask(question) {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  return new Promise((resolve) => rl.question(question, (a) => { rl.close(); resolve(a.trim()); }));
}

function serverConfig() {
  const file = path.join(SERVER, 'config.json');
  return fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : null;
}

// ---------------------------------------------------------------- steps

function checkTools() {
  const major = Number(process.versions.node.split('.')[0]);
  if (major < 20) fail(`Node.js 20 or newer is needed (you have ${process.versions.node}). Get it from https://nodejs.org`);
  const java = spawnSync('java', ['-version'], { shell: WIN });
  if (java.error || java.status !== 0) {
    fail('Java is needed to build Astra. Install a JDK 21 (for example from https://adoptium.net) and try again.');
  }
}

async function setup() {
  const deps = Object.keys(JSON.parse(fs.readFileSync(path.join(LAUNCHER, 'package.json'), 'utf8')).dependencies || {});
  const missing = deps.filter((d) => !fs.existsSync(path.join(LAUNCHER, 'node_modules', d)));
  if (!fs.existsSync(path.join(LAUNCHER, 'node_modules', 'electron')) || missing.length) {
    run('npm', ['install', '--no-audit', '--no-fund'], LAUNCHER, 'Installing the app\'s packages...');
  }
  if (!serverConfig()) {
    const example = JSON.parse(fs.readFileSync(path.join(SERVER, 'config.example.json'), 'utf8'));
    const name = await ask('Your Minecraft name (you\'ll be the Owner): ');
    example.owners = name ? [name] : [];
    fs.writeFileSync(path.join(SERVER, 'config.json'), JSON.stringify(example, null, 2) + '\n');
    say('Saved server/config.json. Edit it any time to add owners.');
  }
}

function build() {
  run(WIN ? 'gradlew.bat' : './gradlew', ['build', '--console=plain'], ROOT, 'Building the Minecraft client (this takes a while the first time)...');
  say('Client built.');
}

async function serverRunning(port) {
  for (const host of ['127.0.0.1', '[::1]']) {
    try {
      const res = await fetch(`http://${host}:${port}/healthz`, { signal: AbortSignal.timeout(1500) });
      const body = await res.json();
      if (body && body.server === 'astra') return true;
    } catch {
      // not running here
    }
  }
  return false;
}

async function startServerWindow() {
  const port = (serverConfig() || {}).port || 8787;
  if (await serverRunning(port)) {
    say(`The Astra server is already running on port ${port}.`);
    return;
  }
  say(`Starting the Astra server on port ${port} (in its own window)...`);
  if (WIN) {
    spawn(`start "Astra Server" /d "${SERVER}" cmd /k node src\\index.js`, { detached: true, stdio: 'ignore', shell: true }).unref();
  } else {
    spawn('node', ['src/index.js'], { cwd: SERVER, detached: true, stdio: 'ignore' }).unref();
  }
  for (let i = 0; i < 20 && !(await serverRunning(port)); i++) await new Promise((r) => setTimeout(r, 500));
  if (!(await serverRunning(port))) say('The server didn\'t answer yet. Check the "Astra Server" window for errors.');
}

function startApp() {
  say('Opening the Astra app...');
  const app = spawn('npm', ['start'], { cwd: LAUNCHER, stdio: 'inherit', shell: WIN });
  app.on('exit', () => say('The Astra app closed. The server keeps running in its own window.'));
}

function installer() {
  const cfg = serverConfig();
  const serverUrl = cfg && cfg.publicUrl ? cfg.publicUrl : 'http://localhost:8787';
  if (/localhost|127\.0\.0\.1/.test(serverUrl)) {
    say(`Note: the installer will point players at ${serverUrl}. Set "publicUrl" in server/config.json to your hosted address first if friends will use it.`);
  }
  fs.writeFileSync(path.join(LAUNCHER, 'build-config.json'), JSON.stringify({ serverUrl }, null, 2) + '\n');
  run('npm', ['run', 'dist'], LAUNCHER, 'Packing the Windows installer...');
  say(`Done. The installer is in ${path.join(LAUNCHER, 'dist')}`);
}

// ---------------------------------------------------------------- commands

(async () => {
  const cmd = process.argv[2] || 'start';
  process.chdir(ROOT);
  if (cmd === 'build') {
    checkTools();
    build();
  } else if (cmd === 'server') {
    await setup();
    require(path.join(SERVER, 'src', 'index.js'));
  } else if (cmd === 'installer') {
    checkTools();
    await setup();
    build();
    installer();
  } else if (cmd === 'start') {
    checkTools();
    await setup();
    build();
    await startServerWindow();
    startApp();
  } else {
    fail(`Unknown command "${cmd}". Use start, build, server or installer.`);
  }
})();
