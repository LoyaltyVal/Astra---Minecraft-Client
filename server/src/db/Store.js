'use strict';

const fs = require('fs');
const path = require('path');

/**
 * A small JSON document store. The whole database lives in memory and is written to
 * data/db.json shortly after each change (atomically, via a temp file), so a crash can't leave
 * a half-written file. Plenty for a client with thousands of players; swap for SQL later if needed.
 */
class Store {
  constructor(file) {
    this.file = file;
    this.data = {
      players: {},     // uuid -> player
      cosmetics: {},   // id -> cosmetic
      sessions: {},    // sha256(token) -> session
      audit: []        // newest last
    };
    this.saveTimer = null;
  }

  load() {
    fs.mkdirSync(path.dirname(this.file), { recursive: true });
    if (fs.existsSync(this.file)) {
      const saved = JSON.parse(fs.readFileSync(this.file, 'utf8'));
      this.data = { ...this.data, ...saved };
    }
    return this;
  }

  get players() { return this.data.players; }
  get cosmetics() { return this.data.cosmetics; }
  get sessions() { return this.data.sessions; }
  get audit() { return this.data.audit; }

  /** Marks the store dirty; it's written to disk within 250 ms. */
  save() {
    if (this.saveTimer) return;
    this.saveTimer = setTimeout(() => {
      this.saveTimer = null;
      this.flush();
    }, 250);
  }

  flush() {
    if (this.saveTimer) {
      clearTimeout(this.saveTimer);
      this.saveTimer = null;
    }
    const tmp = this.file + '.tmp';
    fs.writeFileSync(tmp, JSON.stringify(this.data, null, 1));
    fs.renameSync(tmp, this.file);
  }
}

module.exports = { Store };
