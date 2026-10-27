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

// Everything printed is also saved to astra-last-run.log (handy when asking for help).
const LOG = path.join(ROOT, 'astra-last-run.log');
fs.writeFileSync(LOG, `Astra ${process.argv[2] || 'start'} - ${new Date().toISOString()}\n`);
const logLine = (text) => fs.appendFileSync(LOG, String(text).replace(/\x1b\[[0-9;]*m/g, ''));

const say = (msg) => {
  console.log(`\x1b[35m[Astra]\x1b[0m ${msg}`);
  logLine(`[Astra] ${msg}\n`);
};
const fail = (msg) => {
  console.error(`\x1b[31m[Astra] ${msg}\x1b[0m`);
  logLine(`[Astra] ERROR: ${msg}\n`);
  console.error(`Details were saved to ${LOG}`);
  process.exit(1);
};

/** Runs a command, showing its output live and saving it to the log. */
function run(cmd, args, cwd, label, env = process.env) {
  say(label);
  return new Promise((resolve) => {
    const child = spawn(cmd, args, { cwd, shell: WIN, env: { ...env, FORCE_COLOR: '0' } });
    const pass = (out) => (chunk) => {
      out.write(chunk);
      logLine(chunk);
    };
    child.stdout.on('data', pass(process.stdout));
    child.stderr.on('data', pass(process.stderr));
    child.on('error', (e) => fail(`${label} couldn't start: ${e.message}`));
    child.on('close', (code) => {
      if (code !== 0) fail(`${label} failed (exit code ${code}). Scroll up for the error.`);
      resolve();
    });
  });
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

/** The major version of a JDK folder, from its `release` file (0 if it isn't a JDK). */
function jdkVersion(home) {
  try {
    const release = fs.readFileSync(path.join(home, 'release'), 'utf8');
    const m = /JAVA_VERSION="(1\.)?(\d+)/.exec(release);
    const javac = path.join(home, 'bin', WIN ? 'javac.exe' : 'javac');
    return m && fs.existsSync(javac) ? Number(m[2]) : 0;
  } catch {
    return 0;
  }
}

/**
 * Gradle needs Java 21, but the `java` on PATH is often an old Java 8 (Minecraft players have
 * plenty of those). Looks through JAVA_HOME and the usual install folders and picks the newest JDK.
 */
function findJdk() {
  const roots = [];
  if (process.env.JAVA_HOME) roots.push({ dir: process.env.JAVA_HOME, single: true });
  const home = require('os').homedir();
  roots.push({ dir: path.join(home, '.jdks') });
  if (WIN) {
    for (const pf of [process.env.ProgramFiles, process.env['ProgramFiles(x86)']].filter(Boolean)) {
      for (const vendor of ['Java', 'Eclipse Adoptium', 'Microsoft', 'Zulu', 'BellSoft', 'OpenLogic', 'Amazon Corretto', 'Semeru']) {
        roots.push({ dir: path.join(pf, vendor) });
      }
    }
  } else {
    roots.push({ dir: '/usr/lib/jvm' }, { dir: '/Library/Java/JavaVirtualMachines' });
  }
  const found = [];
  for (const { dir, single } of roots) {
    const homes = single ? [dir] : (() => {
      try {
        return fs.readdirSync(dir).map((n) => path.join(dir, n)).flatMap((h) => [h, path.join(h, 'Contents', 'Home')]);
      } catch {
        return [];
      }
    })();
    for (const h of homes) {
      const v = jdkVersion(h);
      if (v) found.push({ home: h, version: v });
    }
  }
  found.sort((a, b) => b.version - a.version);
  return found.find((j) => j.version >= 21) || null;
}

let gradleEnv = process.env;

function checkTools() {
  const major = Number(process.versions.node.split('.')[0]);
  if (major < 20) fail(`Node.js 20 or newer is needed (you have ${process.versions.node}). Get it from https://nodejs.org`);
  const jdk = findJdk();
  if (!jdk) {
    fail('Building Astra needs Java 21 (a JDK). Install it from https://adoptium.net (choose JDK 21), then run this again.');
  }
  say(`Using Java ${jdk.version} from ${jdk.home}`);
  gradleEnv = { ...process.env, JAVA_HOME: jdk.home, PATH: `${path.join(jdk.home, 'bin')}${path.delimiter}${process.env.PATH}` };
}

async function setup() {
  const deps = Object.keys(JSON.parse(fs.readFileSync(path.join(LAUNCHER, 'package.json'), 'utf8')).dependencies || {});
  const missing = deps.filter((d) => !fs.existsSync(path.join(LAUNCHER, 'node_modules', d)));
  if (!fs.existsSync(path.join(LAUNCHER, 'node_modules', 'electron')) || missing.length) {
    await run('npm', ['install', '--no-audit', '--no-fund'], LAUNCHER, 'Installing the app\'s packages...');
  }
  if (!serverConfig()) {
    const example = JSON.parse(fs.readFileSync(path.join(SERVER, 'config.example.json'), 'utf8'));
    const name = await ask('Your Minecraft name (you\'ll be the Owner): ');
    example.owners = name ? [name] : [];
    fs.writeFileSync(path.join(SERVER, 'config.json'), JSON.stringify(example, null, 2) + '\n');
    say('Saved server/config.json. Edit it any time to add owners.');
  }
}

async function build() {
  await run(WIN ? 'gradlew.bat' : './gradlew', ['build', '--console=plain'], ROOT, 'Building the Minecraft client (this takes a while the first time)...', gradleEnv);
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

async function installer() {
  const cfg = serverConfig();
  const serverUrl = cfg && cfg.publicUrl ? cfg.publicUrl : 'http://localhost:8787';
  if (/localhost|127\.0\.0\.1/.test(serverUrl)) {
    say(`Note: the installer will point players at ${serverUrl}. Set "publicUrl" in server/config.json to your hosted address first if friends will use it.`);
  }
  fs.writeFileSync(path.join(LAUNCHER, 'build-config.json'), JSON.stringify({ serverUrl }, null, 2) + '\n');
  await run('npm', ['run', 'dist'], LAUNCHER, 'Packing the Windows installer...');
  say(`Done. The installer is in ${path.join(LAUNCHER, 'dist')}`);
}

// ---------------------------------------------------------------- commands

(async () => {
  const cmd = process.argv[2] || 'start';
  process.chdir(ROOT);
  process.on('uncaughtException', (e) => fail(e.stack || e.message));
  process.on('unhandledRejection', (e) => fail((e && e.stack) || String(e)));
  if (cmd === 'build') {
    checkTools();
    await build();
  } else if (cmd === 'server') {
    await setup();
    require(path.join(SERVER, 'src', 'index.js'));
  } else if (cmd === 'installer') {
    checkTools();
    await setup();
    await build();
    await installer();
  } else if (cmd === 'start') {
    checkTools();
    await setup();
    await build();
    await startServerWindow();
    startApp();
  } else {
    fail(`Unknown command "${cmd}". Use start, build, server or installer.`);
  }
})();
