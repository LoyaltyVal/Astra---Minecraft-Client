'use strict';

const MAX_ENTRIES = 2000;

/** Records staff actions (rank changes, bans, grants, uploads) for the admin panel's Activity tab. */
class AuditLog {
  constructor(store) {
    this.store = store;
  }

  record(actor, action, target, detail) {
    this.store.audit.push({
      at: new Date().toISOString(),
      actor: actor ? { uuid: actor.uuid, name: actor.name } : null,
      action,
      target: target || null,
      detail: detail || null
    });
    if (this.store.audit.length > MAX_ENTRIES) {
      this.store.audit.splice(0, this.store.audit.length - MAX_ENTRIES);
    }
    this.store.save();
  }

  recent(limit = 100) {
    return this.store.audit.slice(-limit).reverse();
  }
}

module.exports = { AuditLog };
