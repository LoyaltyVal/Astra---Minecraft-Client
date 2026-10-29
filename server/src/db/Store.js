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
    this.backup();
  }

  /** Keeps one copy of the database per day in data/backups (the last 14 days). */
  backup() {
    const day = new Date().toISOString().slice(0, 10);
    if (this.lastBackup === day) return;
    this.lastBackup = day;
    try {
      const dir = path.join(path.dirname(this.file), 'backups');
      fs.mkdirSync(dir, { recursive: true });
      const target = path.join(dir, `db-${day}.json`);
      if (!fs.existsSync(target)) fs.copyFileSync(this.file, target);
      const old = fs.readdirSync(dir).filter((f) => /^db-\d{4}-\d{2}-\d{2}\.json$/.test(f)).sort();
      for (const f of old.slice(0, Math.max(0, old.length - 14))) fs.rmSync(path.join(dir, f), { force: true });
    } catch (e) {
      console.warn('[astra] Backup failed:', e.message);
    }
  }
}

module.exports = { Store };
