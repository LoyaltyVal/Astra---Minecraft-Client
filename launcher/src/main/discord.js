const net = require('net');
const path = require('path');

/**
 * Discord Rich Presence ("Playing Astra Client · On mc.hypixel.net") over Discord's local IPC pipe,
 * with no extra packages. It needs a Discord application ID (the server's "discordClientId");
 * without one, or when Discord isn't running, it quietly does nothing.
 *
 * Protocol: each frame is [opcode int32 LE][length int32 LE][JSON]. Opcode 0 = handshake,
 * 1 = command, 2 = close.
 */

function pipePath(i) {
  if (process.platform === 'win32') return `\\\\?\\pipe\\discord-ipc-${i}`;
  const dir = process.env.XDG_RUNTIME_DIR || process.env.TMPDIR || process.env.TMP || '/tmp';
  return path.join(dir, `discord-ipc-${i}`);
}

function encode(op, data) {
  const json = Buffer.from(JSON.stringify(data));
  const head = Buffer.alloc(8);
  head.writeInt32LE(op, 0);
  head.writeInt32LE(json.length, 4);
  return Buffer.concat([head, json]);
}

function connect(i = 0) {
  return new Promise((resolve, reject) => {
    const socket = net.createConnection(pipePath(i));
    socket.once('connect', () => resolve(socket));
    socket.once('error', () => {
      if (i < 9) connect(i + 1).then(resolve, reject);
      else reject(new Error('Discord is not running.'));
    });
  });
}

class DiscordPresence {
  constructor(log) {
    this.log = log;
    this.socket = null;
    this.clientId = null;
    this.started = null;
  }

  /** Connects (once per game session). Returns false when Discord isn't available. */
  async start(clientId) {
    if (!/^\d{15,25}$/.test(String(clientId || ''))) return false;
    this.stop();
    try {
      this.socket = await connect();
    } catch {
      return false;
    }
    this.clientId = String(clientId);
    this.started = Date.now();
    this.socket.on('error', () => this.stop());
    this.socket.on('close', () => { this.socket = null; });
    // Discord answers the handshake with a READY event; commands sent before it are ignored.
    const ready = new Promise((resolve) => {
      const timer = setTimeout(() => resolve(false), 4000);
      this.socket.once('data', () => {
        clearTimeout(timer);
        resolve(true);
      });
    });
    this.socket.write(encode(0, { v: 1, client_id: this.clientId }));
    if (!(await ready)) {
      this.stop();
      return false;
    }
    this.socket.on('data', () => { /* later replies aren't needed */ });
    return true;
  }

  /** Shows what you're doing. `server` is a hostname, "singleplayer", or null (menus / hidden). */
  update({ version, server, hidden, website }) {
    if (!this.socket) return;
    const state = hidden ? 'In game'
      : server === 'singleplayer' ? 'Playing singleplayer'
        : server ? `On ${server}`
          : 'In the menus';
    const activity = {
      details: `Minecraft ${version}`,
      state,
      timestamps: { start: Math.floor(this.started / 1000) },
      assets: { large_image: 'astra', large_text: 'Astra Client' },
      instance: false
    };
    // Discord only accepts https links on buttons.
    if (website && website.startsWith('https://')) activity.buttons = [{ label: 'Get Astra', url: website }];
    this.socket.write(encode(1, {
      cmd: 'SET_ACTIVITY',
      args: { pid: process.pid, activity },
      nonce: `${Date.now()}-${Math.random()}`
    }));
  }

  stop() {
    if (!this.socket) return;
    try {
      this.socket.write(encode(1, { cmd: 'SET_ACTIVITY', args: { pid: process.pid }, nonce: `${Date.now()}` }));
      this.socket.end(encode(2, {}));
    } catch {
      // already gone
    }
    this.socket = null;
  }
}

module.exports = { DiscordPresence, encode };
