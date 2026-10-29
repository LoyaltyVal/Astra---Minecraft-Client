#!/usr/bin/env node
'use strict';

/**
 * One script to run Astra. Double-click "Start Astra.bat" (or run `node tools/astra.js`).
 *
 *   node tools/astra.js            set up if needed, build, start the server, open the app
 *   node tools/astra.js build      build the Minecraft client jars only
 *   node tools/astra.js server     start the server only (in this window)
 *   node tools/astra.js installer  build a Windows installer for players (launcher/dist)
 *   node tools/astra.js host       put the server online through Cloudflare Tunnel (free HTTPS)
 *   node tools/astra.js address    one-time setup of a permanent address on your own domain
 *   node tools/astra.js duckdns    one-time setup of a free yourname.duckdns.org address
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
  // With shell: true a path containing spaces must be quoted.
  if (WIN && /\s/.test(cmd) && !cmd.startsWith('"')) cmd = `"${cmd}"`;
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
  await installLauncherPackages();
  await setupServerConfig();
}

async function installLauncherPackages() {
  const deps = Object.keys(JSON.parse(fs.readFileSync(path.join(LAUNCHER, 'package.json'), 'utf8')).dependencies || {});
  const missing = deps.filter((d) => !fs.existsSync(path.join(LAUNCHER, 'node_modules', d)));
  if (!fs.existsSync(path.join(LAUNCHER, 'node_modules', 'electron')) || missing.length) {
    await run('npm', ['install', '--no-audit', '--no-fund'], LAUNCHER, 'Installing the app\'s packages...');
  }
}

async function setupServerConfig() {
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

/**
 * electron-builder unpacks a "winCodeSign" tool bundle that contains macOS symlinks; creating those
 * fails on Windows unless Developer Mode is on ("Cannot create symbolic link: A required privilege is
 * not held by the client"). Unpack it ourselves without the macOS part so the build never needs it.
 */
async function prepareWinCodeSign() {
  if (!WIN || !process.env.LOCALAPPDATA) return;
  const cache = path.join(process.env.LOCALAPPDATA, 'electron-builder', 'Cache', 'winCodeSign');
  const target = path.join(cache, 'winCodeSign-2.6.0');
  if (fs.existsSync(path.join(target, 'windows-10'))) return;
  fs.mkdirSync(cache, { recursive: true });
  const archive = path.join(cache, 'winCodeSign-2.6.0.7z');
  if (!fs.existsSync(archive)) {
    say('Downloading the Windows packaging tools...');
    const res = await fetch('https://github.com/electron-userland/electron-builder-binaries/releases/download/winCodeSign-2.6.0/winCodeSign-2.6.0.7z');
    if (!res.ok) fail(`Couldn't download the Windows packaging tools (${res.status}).`);
    fs.writeFileSync(archive, Buffer.from(await res.arrayBuffer()));
  }
  const sevenZip = path.join(LAUNCHER, 'node_modules', '7zip-bin', 'win', process.arch === 'arm64' ? 'arm64' : 'x64', '7za.exe');
  fs.rmSync(target, { recursive: true, force: true });
  await run(sevenZip, ['x', '-bd', '-y', `-o${target}`, archive, '-xr!darwin'], cache, 'Unpacking the Windows packaging tools...');
}

/** The address installers point players at; stops before a long build if it can't work. */
function installerAddress() {
  const cfg = serverConfig();
  const serverUrl = cfg && cfg.publicUrl ? cfg.publicUrl : 'http://localhost:8787';
  if (/\.trycloudflare\.com/i.test(serverUrl)) {
    // A quick-tunnel address dies the next time hosting restarts; every installer built with it
    // would point players at a dead server for good.
    fail([
      `server/config.json "publicUrl" is a temporary address (${serverUrl}).`,
      'It changes every time hosting restarts, so installers built with it stop working.',
      'Set up a permanent address first: run "Set Up DuckDNS.bat" (free) or',
      '"Set Up Permanent Address.bat" (your own domain), then build the installer again.'
    ].join('\n'));
  }
  if (/localhost|127\.0\.0\.1/.test(serverUrl)) {
    say(`Note: the installer will point players at ${serverUrl}, which only works on this PC. Set up a permanent address (Set Up DuckDNS.bat) before giving it to players.`);
  }
  return serverUrl;
}

async function installer() {
  const serverUrl = installerAddress();
  fs.writeFileSync(path.join(LAUNCHER, 'build-config.json'), JSON.stringify({ serverUrl }, null, 2) + '\n');
  await prepareWinCodeSign();
  await run('npm', ['run', 'dist'], LAUNCHER, 'Packing the Windows installer...');
  say(`Done. The installer is in ${path.join(LAUNCHER, 'dist')}`);
  // Copy it next to the server so the website's Download button serves it.
  const dist = path.join(LAUNCHER, 'dist');
  const exes = fs.readdirSync(dist).filter((f) => /\.exe$/i.test(f) && !/uninstall/i.test(f))
    .map((f) => ({ f, t: fs.statSync(path.join(dist, f)).mtimeMs })).sort((a, b) => b.t - a.t);
  if (exes.length) {
    fs.mkdirSync(path.join(SERVER, 'downloads'), { recursive: true });
    const target = path.join(SERVER, 'downloads', 'Astra-Setup.exe');
    fs.copyFileSync(path.join(dist, exes[0].f), target);
    // What the website shows next to its Download button.
    const pkg = JSON.parse(fs.readFileSync(path.join(LAUNCHER, 'package.json'), 'utf8'));
    fs.writeFileSync(path.join(SERVER, 'downloads', 'Astra-Setup.json'), JSON.stringify({
      version: pkg.version,
      size: fs.statSync(target).size,
      builtAt: new Date().toISOString(),
      versions: fs.readdirSync(path.join(LAUNCHER, 'mods')).filter((v) => /^\d/.test(v))
    }, null, 2) + '\n');
    say(`The website's Download button now serves ${exes[0].f} (${serverUrl}/download/Astra-Setup.exe).`);
  }
}

// ---------------------------------------------------------------- hosting (Cloudflare Tunnel)

const BIN = path.join(ROOT, 'tools', 'bin');

function saveServerConfig(cfg) {
  fs.writeFileSync(path.join(SERVER, 'config.json'), JSON.stringify(cfg, null, 2) + '\n');
}

/** cloudflared: on PATH, or downloaded once into tools/bin. */
async function cloudflared() {
  const which = spawnSync(WIN ? 'where' : 'which', ['cloudflared'], { encoding: 'utf8' });
  if (which.status === 0 && which.stdout.trim()) return which.stdout.trim().split(/\r?\n/)[0];
  const exe = path.join(BIN, WIN ? 'cloudflared.exe' : 'cloudflared');
  if (fs.existsSync(exe)) return exe;
  if (process.platform === 'darwin') fail('Install cloudflared first: brew install cloudflared');
  const file = WIN ? 'cloudflared-windows-amd64.exe' : `cloudflared-linux-${process.arch === 'arm64' ? 'arm64' : 'amd64'}`;
  say('Downloading Cloudflare Tunnel (cloudflared, about 50 MB, one time only)...');
  const res = await fetch(`https://github.com/cloudflare/cloudflared/releases/latest/download/${file}`);
  if (!res.ok) fail(`Couldn't download cloudflared (${res.status}). Get it from https://github.com/cloudflare/cloudflared/releases and put it in ${BIN}`);
  fs.mkdirSync(BIN, { recursive: true });
  fs.writeFileSync(exe, Buffer.from(await res.arrayBuffer()));
  if (!WIN) fs.chmodSync(exe, 0o755);
  return exe;
}

/** Things that must be right before strangers can reach the server. */
async function checkSafeToHost() {
  await setupServerConfig();
  const cfg = serverConfig();
  if (cfg.devAuth) {
    say('"devAuth" is ON in server/config.json. It skips the Mojang check, so online anyone could sign in as you (the Owner).');
    const a = await ask('Turn devAuth off now? (Y/n) ');
    if (/^n/i.test(a)) fail('Not hosting with devAuth on. Set "devAuth": false in server/config.json.');
    cfg.devAuth = false;
    saveServerConfig(cfg);
    say('devAuth is off.');
  }
  if (!cfg.owners || !cfg.owners.length) {
    const name = await ask('Your Minecraft name (you\'ll be the Owner): ');
    if (!name) fail('Set "owners" in server/config.json first.');
    cfg.owners = [name];
    saveServerConfig(cfg);
  }
  return cfg;
}

function banner(lines) {
  const width = Math.max(...lines.map((l) => l.length)) + 4;
  const bar = '='.repeat(width);
  console.log(`\n\x1b[35m${bar}\x1b[0m`);
  for (const l of lines) console.log(`  ${l}`);
  console.log(`\x1b[35m${bar}\x1b[0m\n`);
  logLine(lines.join('\n') + '\n');
}

let shuttingDown = false;

/**
 * Runs the Astra server and starts it again if it ever stops on its own (a crash shouldn't take
 * everyone offline until you notice). Returns a handle whose kill() stops it for good.
 */
function superviseServer(publicUrl) {
  let child = null;
  let restarts = 0;
  let stopped = false;
  const start = () => {
    const startedAt = Date.now();
    child = startServerChild(publicUrl);
    child.on('exit', (code) => {
      if (stopped || shuttingDown) return;
      if (Date.now() - startedAt > 60000) restarts = 0; // it had been running fine
      restarts++;
      const wait = Math.min(30000, 1000 * 2 ** Math.min(restarts, 5));
      say(`The Astra server stopped (exit code ${code}). Starting it again in ${Math.round(wait / 1000)}s...`);
      setTimeout(start, wait);
    });
  };
  start();
  return {
    kill() {
      stopped = true;
      if (child) child.kill();
    }
  };
}

/** Starts the Astra server as a child process with the public address. */
function startServerChild(publicUrl) {
  const child = spawn(process.execPath, ['src/index.js'], {
    cwd: SERVER,
    stdio: ['ignore', 'pipe', 'pipe'],
    env: { ...process.env, ASTRA_PUBLIC_URL: publicUrl, ASTRA_TRUST_PROXY: '1' }
  });
  const pass = (out) => (chunk) => {
    out.write(chunk);
    logLine(chunk);
  };
  child.stdout.on('data', pass(process.stdout));
  child.stderr.on('data', pass(process.stderr));
  return child;
}

async function host() {
  const cfg = await checkSafeToHost();
  const port = cfg.port || 8787;
  if (await serverRunning(port)) {
    fail(`An Astra server is already running on port ${port}. Close its window first (it was started without the online address).`);
  }
  if (cfg.duckdns && cfg.duckdns.domain) return hostDuckDns(cfg);
  const exe = await cloudflared();
  const named = cfg.tunnel && cfg.tunnel.name && cfg.tunnel.hostname && fs.existsSync(cfg.tunnel.configFile || '');
  const children = [];
  const stop = () => {
    shuttingDown = true;
    for (const c of children) {
      try { c.kill(); } catch { /* gone */ }
    }
    process.exit(0);
  };
  process.on('SIGINT', stop);
  process.on('SIGTERM', stop);

  let publicUrl;
  if (named) {
    publicUrl = `https://${cfg.tunnel.hostname}`;
    say(`Connecting ${publicUrl} through your Cloudflare tunnel...`);
    const tunnel = spawn(exe, ['tunnel', '--no-autoupdate', '--config', cfg.tunnel.configFile, 'run', cfg.tunnel.name], { stdio: ['ignore', 'ignore', 'pipe'] });
    tunnel.stderr.on('data', (c) => logLine(c));
    tunnel.on('exit', (code) => { say(`Cloudflare Tunnel stopped (exit code ${code}). Players can't connect until you run this again.`); stop(); });
    children.push(tunnel);
  } else {
    say('Starting a free Cloudflare quick tunnel...');
    const tunnel = spawn(exe, ['tunnel', '--no-autoupdate', '--url', `http://localhost:${port}`], { stdio: ['ignore', 'ignore', 'pipe'] });
    children.push(tunnel);
    publicUrl = await new Promise((resolve) => {
      const timer = setTimeout(() => fail('Cloudflare didn\'t give an address within 60 seconds. Check your internet and try again.'), 60000);
      tunnel.stderr.on('data', (c) => {
        logLine(c);
        const m = /https:\/\/[a-z0-9-]+\.trycloudflare\.com/.exec(String(c));
        if (m) {
          clearTimeout(timer);
          resolve(m[0]);
        }
      });
      tunnel.on('exit', (code) => fail(`Cloudflare Tunnel stopped (exit code ${code}).`));
    });
  }

  children.push(superviseServer(publicUrl));
  for (let i = 0; i < 20 && !(await serverRunning(port)); i++) await new Promise((r) => setTimeout(r, 500));
  fs.mkdirSync(path.join(SERVER, 'data'), { recursive: true });
  fs.writeFileSync(path.join(SERVER, 'data', 'online-address.txt'), publicUrl + '\n');

  banner([
    'Astra is ONLINE at:',
    '',
    `    ${publicUrl}`,
    '',
    'Players: install Astra, open Settings in the app and set',
    `"Astra server" to the address above (or use an installer`,
    'built while this address is in server/config.json "publicUrl").',
    '',
    ...(named ? ['This address is permanent.']
      : ['This is a TEST address: it changes every time you run this.', 'For a permanent one run "Set Up Permanent Address.bat".']),
    '',
    'Keep this window open. Close it (or Ctrl+C) to go offline.'
  ]);
}

// ---------------------------------------------------------------- hosting (DuckDNS + Caddy)

/**
 * DuckDNS gives a free name (yourname.duckdns.org) that follows your home IP. Players connect
 * straight to your PC, so the router must forward one port to it. Caddy sits in front of the
 * Astra server and gets a free HTTPS certificate from Let's Encrypt through DuckDNS (DNS check),
 * so only the HTTPS port needs forwarding.
 */

function duckAddress(d) {
  return `https://${d.domain}.duckdns.org${Number(d.port) === 443 ? '' : `:${d.port}`}`;
}

async function duckUpdate(d) {
  const url = `https://www.duckdns.org/update?domains=${encodeURIComponent(d.domain)}&token=${encodeURIComponent(d.token)}&ip=`;
  const res = await fetch(url, { signal: AbortSignal.timeout(15000) });
  return (await res.text()).trim() === 'OK';
}

/** Caddy with the DuckDNS plugin, downloaded once into tools/bin from caddyserver.com. */
async function caddy() {
  const exe = path.join(BIN, WIN ? 'caddy.exe' : 'caddy');
  if (fs.existsSync(exe)) return exe;
  const os = WIN ? 'windows' : process.platform === 'darwin' ? 'darwin' : 'linux';
  const arch = process.arch === 'arm64' ? 'arm64' : 'amd64';
  say('Downloading Caddy with DuckDNS support (about 40 MB, one time only)...');
  const res = await fetch(`https://caddyserver.com/api/download?os=${os}&arch=${arch}&p=${encodeURIComponent('github.com/caddy-dns/duckdns')}`);
  const data = Buffer.from(await res.arrayBuffer());
  if (!res.ok || data.length < 1_000_000) {
    fail(`Couldn't download Caddy (${res.status}). Try again in a minute, or download it with the DuckDNS plugin from https://caddyserver.com/download and save it as ${exe}`);
  }
  fs.mkdirSync(BIN, { recursive: true });
  fs.writeFileSync(exe, data);
  if (!WIN) fs.chmodSync(exe, 0o755);
  return exe;
}

function lanAddress() {
  for (const list of Object.values(require('os').networkInterfaces())) {
    for (const a of list || []) {
      if (a.family === 'IPv4' && !a.internal && /^(192\.168|10\.|172\.(1[6-9]|2\d|3[01]))/.test(a.address)) return a.address;
    }
  }
  return 'this PC\'s local IP (run ipconfig)';
}

async function duckdns() {
  const cfg = await checkSafeToHost();
  say('DuckDNS gives you a free permanent address like https://astraclient.duckdns.org');
  say('1. Go to https://www.duckdns.org and sign in (GitHub, Google, Reddit...).');
  say('2. Add a subdomain (e.g. astraclient). Your token is shown at the top of the page.');
  const domain = (await ask('Subdomain (just the name, without .duckdns.org): ')).toLowerCase().replace(/\.duckdns\.org.*$/, '').replace(/^https?:\/\//, '');
  if (!/^[a-z0-9-]{1,63}$/.test(domain)) fail('That isn\'t a DuckDNS name. Use only letters, numbers and dashes.');
  const token = await ask('DuckDNS token: ');
  if (!/^[0-9a-f-]{36}$/i.test(token)) fail('That doesn\'t look like a DuckDNS token (it looks like 1a2b3c4d-....). Copy it from the top of duckdns.org.');
  const portAnswer = await ask('HTTPS port for players (press Enter for 443, the normal one): ');
  const port = portAnswer ? Number(portAnswer) : 443;
  if (!Number.isInteger(port) || port < 1 || port > 65535 || port === (cfg.port || 8787)) fail('Pick a port between 1 and 65535 that isn\'t the Astra server\'s own port.');
  const d = { domain, token, port };
  say('Checking with DuckDNS...');
  if (!(await duckUpdate(d))) fail('DuckDNS said no. Check the subdomain belongs to your account and the token is right.');
  say(`${domain}.duckdns.org now points at your home internet connection.`);
  await caddy();

  delete cfg.tunnel;
  cfg.duckdns = d;
  cfg.publicUrl = duckAddress(d);
  saveServerConfig(cfg);
  banner([
    `Done. Astra's permanent address is ${cfg.publicUrl}`,
    '',
    'ONE THING YOU HAVE TO DO in your router (usually http://192.168.0.1 or 192.168.1.1,',
    'look for "Port Forwarding", "Virtual Server" or "NAT"):',
    '',
    `    Forward TCP port ${port}  ->  ${lanAddress()}  port ${port}`,
    '',
    'Then:',
    '  1. Run "Build Installer.bat" so the installer points players there.',
    '  2. Run "Host Astra Online.bat" whenever you want to be online.',
    '     (Allow Caddy through Windows Firewall when it asks.)',
    '',
    'Players will see your home IP address (that\'s how DuckDNS works).'
  ]);
}

async function hostDuckDns(cfg) {
  const d = cfg.duckdns;
  const port = cfg.port || 8787;
  const publicUrl = duckAddress(d);
  if (!(await duckUpdate(d).catch(() => false))) {
    fail('DuckDNS rejected the update. Run "Set Up DuckDNS.bat" again to re-enter your token.');
  }
  // Home IPs change; keep DuckDNS pointed at us every 5 minutes.
  setInterval(() => duckUpdate(d).catch(() => false).then((ok) => { if (!ok) say('DuckDNS update failed; will retry.'); }), 5 * 60 * 1000);

  const exe = await caddy();
  const caddyfile = path.join(BIN, 'Caddyfile');
  fs.writeFileSync(caddyfile, [
    '{',
    '\t# Only the HTTPS port is forwarded; don\'t also grab port 80 or the admin port.',
    '\tauto_https disable_redirects',
    '\tadmin off',
    '}',
    '',
    `${d.domain}.duckdns.org:${d.port} {`,
    '\ttls {',
    '\t\tdns duckdns {env.DUCKDNS_TOKEN}',
    '\t}',
    `\treverse_proxy localhost:${port} {`,
    '\t\t# Only Cloudflare may set this; strip it so players can\'t fake their address.',
    '\t\theader_up -CF-Connecting-IP',
    '\t}',
    '}',
    ''
  ].join('\n'));

  const children = [];
  const stop = () => {
    shuttingDown = true;
    for (const c of children) {
      try { c.kill(); } catch { /* gone */ }
    }
    process.exit(0);
  };
  process.on('SIGINT', stop);
  process.on('SIGTERM', stop);

  children.push(superviseServer(publicUrl));
  for (let i = 0; i < 20 && !(await serverRunning(port)); i++) await new Promise((r) => setTimeout(r, 500));

  say('Starting Caddy (HTTPS). The first time it gets a certificate, which takes up to a minute...');
  const proxy = spawn(exe, ['run', '--config', caddyfile, '--adapter', 'caddyfile'], {
    cwd: BIN,
    stdio: ['ignore', 'pipe', 'pipe'],
    env: { ...process.env, DUCKDNS_TOKEN: d.token }
  });
  children.push(proxy);
  const watch = (c) => {
    const text = String(c);
    logLine(text);
    if (/certificate obtained successfully/i.test(text)) say('HTTPS certificate ready.');
    if (/bind: (address already in use|An attempt was made to access a socket)/i.test(text)) {
      say(`Port ${d.port} is already used by another program (Skype, IIS, another web server...). Close it or run "Set Up DuckDNS.bat" with another port.`);
    }
    if (/could not get certificate|obtain.*fail|DNS.*error|unauthorized/i.test(text)) {
      say('Caddy is having trouble getting the HTTPS certificate (it keeps retrying). Details are in astra-last-run.log.');
    }
  };
  proxy.stdout.on('data', watch);
  proxy.stderr.on('data', watch);
  proxy.on('exit', (code) => { say(`Caddy stopped (exit code ${code}). Players can't connect. Details are in astra-last-run.log.`); stop(); });

  fs.mkdirSync(path.join(SERVER, 'data'), { recursive: true });
  fs.writeFileSync(path.join(SERVER, 'data', 'online-address.txt'), publicUrl + '\n');
  banner([
    'Astra is ONLINE at:',
    '',
    `    ${publicUrl}`,
    '',
    `Needs: router forwarding TCP ${d.port} -> ${lanAddress()}.`,
    'Players use installers built with this address, or put it in',
    'the app under Settings -> Astra server.',
    '',
    'Test it from your phone on mobile data (not Wi-Fi): many routers',
    'can\'t open their own public address from inside the house.',
    '',
    'Keep this window open. Close it (or Ctrl+C) to go offline.'
  ]);
}

/** One-time: a permanent https address (needs a domain added to a free Cloudflare account). */
async function address() {
  const cfg = await checkSafeToHost();
  const exe = await cloudflared();
  say('This gives Astra a permanent address like https://play.yourdomain.com');
  say('You need a domain on a free Cloudflare account (cheap domains are about $10 a year at Cloudflare Registrar).');
  const hostname = (await ask('Address to use (e.g. play.yourdomain.com): ')).replace(/^https?:\/\//, '').replace(/\/.*$/, '').toLowerCase();
  if (!/^[a-z0-9-]+(\.[a-z0-9-]+)+$/.test(hostname)) fail('That doesn\'t look like an address. Use something like play.yourdomain.com');

  const home = require('os').homedir();
  if (!fs.existsSync(path.join(home, '.cloudflared', 'cert.pem'))) {
    await run(exe, ['tunnel', 'login'], ROOT, 'A browser opens: sign in to Cloudflare and pick your domain...');
  }
  const name = 'astra';
  const list = () => {
    const r = spawnSync(exe, ['tunnel', 'list', '--output', 'json'], { encoding: 'utf8' });
    try { return JSON.parse(r.stdout || '[]'); } catch { return []; }
  };
  let tunnel = list().find((t) => t.name === name && !t.deleted_at);
  if (!tunnel) {
    await run(exe, ['tunnel', 'create', name], ROOT, 'Creating the "astra" tunnel...');
    tunnel = list().find((t) => t.name === name && !t.deleted_at);
  }
  if (!tunnel) fail('Couldn\'t find the tunnel after creating it. Run "cloudflared tunnel list" to check.');
  const creds = path.join(home, '.cloudflared', `${tunnel.id}.json`);
  if (!fs.existsSync(creds)) fail(`The tunnel's key file is missing (${creds}). Delete the tunnel in the Cloudflare dashboard and run this again.`);
  const route = spawnSync(exe, ['tunnel', 'route', 'dns', '--overwrite-dns', name, hostname], { encoding: 'utf8' });
  logLine((route.stdout || '') + (route.stderr || ''));
  if (route.status !== 0) fail(`Couldn't point ${hostname} at the tunnel:\n${route.stderr}`);

  const port = cfg.port || 8787;
  fs.mkdirSync(BIN, { recursive: true });
  const configFile = path.join(BIN, 'astra-tunnel.yml');
  fs.writeFileSync(configFile, [
    `tunnel: ${tunnel.id}`,
    `credentials-file: ${JSON.stringify(creds)}`,
    'ingress:',
    `  - hostname: ${hostname}`,
    `    service: http://localhost:${port}`,
    '  - service: http_status:404',
    ''
  ].join('\n'));
  delete cfg.duckdns;
  cfg.tunnel = { name, hostname, configFile };
  cfg.publicUrl = `https://${hostname}`;
  saveServerConfig(cfg);
  banner([
    `Done. Astra's permanent address is https://${hostname}`,
    '',
    'Next:',
    '  1. Run "Build Installer.bat" so the installer points players there.',
    '  2. Run "Host Astra Online.bat" whenever you want the server online.',
    `  3. Players download Astra from https://${hostname}`
  ]);
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
    installerAddress(); // before the build, so a bad address fails fast
    await build();
    await installer();
  } else if (cmd === 'host') {
    await host();
  } else if (cmd === 'address') {
    await address();
  } else if (cmd === 'duckdns') {
    await duckdns();
  } else if (cmd === 'start') {
    checkTools();
    await setup();
    await build();
    await startServerWindow();
    startApp();
  } else {
    fail(`Unknown command "${cmd}". Use start, build, server, installer, host, address or duckdns.`);
  }
})();
