'use strict';

const crypto = require('crypto');

const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';
const MAX_PAYLOAD = 64 * 1024;

/**
 * A minimal WebSocket (RFC 6455) server connection: text frames, ping/pong and close. Clients
 * only ever send tiny control messages here, so fragmented and binary frames are simply ignored.
 */
class WebSocketConnection {
  constructor(socket, { onText, onClose } = {}) {
    this.socket = socket;
    this.buffer = Buffer.alloc(0);
    this.open = true;
    this.lastSeen = Date.now();
    this.onText = onText || (() => {});
    this.onClose = onClose || (() => {});
    socket.setNoDelay(true);
    socket.on('data', (chunk) => this.receive(chunk));
    socket.on('close', () => this.closed());
    socket.on('error', () => this.closed());
  }

  /** Completes the HTTP upgrade. Returns false (after answering) if the request isn't a valid one. */
  static accept(req, socket, protocol) {
    const key = req.headers['sec-websocket-key'];
    if (!key || String(req.headers.upgrade || '').toLowerCase() !== 'websocket' || req.headers['sec-websocket-version'] !== '13') {
      WebSocketConnection.reject(socket, 400, 'Bad Request');
      return false;
    }
    const acceptKey = crypto.createHash('sha1').update(key + GUID).digest('base64');
    const lines = [
      'HTTP/1.1 101 Switching Protocols',
      'Upgrade: websocket',
      'Connection: Upgrade',
      `Sec-WebSocket-Accept: ${acceptKey}`
    ];
    if (protocol) lines.push(`Sec-WebSocket-Protocol: ${protocol}`);
    socket.write(lines.join('\r\n') + '\r\n\r\n');
    return true;
  }

  static reject(socket, status, text) {
    try {
      socket.end(`HTTP/1.1 ${status} ${text}\r\nConnection: close\r\nContent-Length: 0\r\n\r\n`);
    } catch {
      // already gone
    }
  }

  receive(chunk) {
    this.lastSeen = Date.now();
    this.buffer = Buffer.concat([this.buffer, chunk]);
    while (this.open) {
      const b = this.buffer;
      if (b.length < 2) return;
      const opcode = b[0] & 0x0f;
      const masked = (b[1] & 0x80) !== 0;
      let len = b[1] & 0x7f;
      let offset = 2;
      if (len === 126) {
        if (b.length < 4) return;
        len = b.readUInt16BE(2);
        offset = 4;
      } else if (len === 127) {
        if (b.length < 10) return;
        const big = b.readBigUInt64BE(2);
        if (big > BigInt(MAX_PAYLOAD)) return this.close(1009);
        len = Number(big);
        offset = 10;
      }
      if (len > MAX_PAYLOAD) return this.close(1009);
      if (!masked) return this.close(1002); // clients must mask
      if (b.length < offset + 4 + len) return;
      const mask = b.subarray(offset, offset + 4);
      const payload = Buffer.from(b.subarray(offset + 4, offset + 4 + len));
      for (let i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
      this.buffer = b.subarray(offset + 4 + len);

      if (opcode === 0x1) {
        try {
          this.onText(payload.toString('utf8'));
        } catch {
          // a bad client message never takes the server down
        }
      } else if (opcode === 0x8) {
        return this.close(1000);
      } else if (opcode === 0x9) {
        this.frame(0xA, payload);
      }
      // 0xA (pong), 0x2 (binary) and continuations: nothing to do
    }
  }

  frame(opcode, payload) {
    if (!this.open) return;
    const len = payload.length;
    let head;
    if (len < 126) {
      head = Buffer.from([0x80 | opcode, len]);
    } else if (len < 65536) {
      head = Buffer.alloc(4);
      head[0] = 0x80 | opcode;
      head[1] = 126;
      head.writeUInt16BE(len, 2);
    } else {
      head = Buffer.alloc(10);
      head[0] = 0x80 | opcode;
      head[1] = 127;
      head.writeBigUInt64BE(BigInt(len), 2);
    }
    try {
      this.socket.write(Buffer.concat([head, payload]));
    } catch {
      this.closed();
    }
  }

  send(value) {
    this.frame(0x1, Buffer.from(typeof value === 'string' ? value : JSON.stringify(value), 'utf8'));
  }

  ping() {
    this.frame(0x9, Buffer.alloc(0));
  }

  close(code = 1000) {
    if (!this.open) return;
    const body = Buffer.alloc(2);
    body.writeUInt16BE(code, 0);
    this.frame(0x8, body);
    this.open = false;
    try {
      this.socket.end();
    } catch {
      // gone
    }
    this.onClose(this);
  }

  closed() {
    if (!this.open) return;
    this.open = false;
    try {
      this.socket.destroy();
    } catch {
      // gone
    }
    this.onClose(this);
  }
}

module.exports = { WebSocketConnection };
