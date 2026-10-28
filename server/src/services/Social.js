'use strict';

/**
 * Friends, friend requests, private messages and emotes.
 *
 * Stored on the player records (p.friends = [uuid], p.requests = [uuid of people asking]) plus a
 * capped message log in the store. Everything here is plain data; the routes decide who may call it.
 */

const EMOTES = ['wave', 'dab', 'tpose', 'facepalm', 'cheer', 'point', 'clap', 'salute', 'shrug', 'bow'];
const EMOTE_MS = 4000;
const MAX_MESSAGES = 5000;
const MAX_FRIENDS = 200;

class SocialService {
  constructor(store, players) {
    this.store = store;
    this.players = players;
    if (!Array.isArray(store.data.messages)) store.data.messages = [];
    if (!store.data.nextMessageId) store.data.nextMessageId = 1;
    this.lastSent = new Map();
  }

  static ensure(p) {
    if (!Array.isArray(p.friends)) p.friends = [];
    if (!Array.isArray(p.requests)) p.requests = [];
    return p;
  }

  areFriends(a, b) {
    return SocialService.ensure(a).friends.includes(b.uuid);
  }

  /** Sends a request, or accepts straight away if they already asked you. */
  request(from, to) {
    SocialService.ensure(from);
    SocialService.ensure(to);
    if (from.uuid === to.uuid) throw new Error('You can\'t add yourself.');
    if (from.friends.includes(to.uuid)) throw new Error(`You and ${to.name} are already friends.`);
    if (from.friends.length >= MAX_FRIENDS) throw new Error('Your friend list is full.');
    if (from.requests.includes(to.uuid)) {
      this.accept(from, to);
      return 'accepted';
    }
    if (to.requests.includes(from.uuid)) throw new Error(`You already asked ${to.name}.`);
    if (to.friendRequests === 'off') throw new Error(`${to.name} isn't accepting friend requests.`);
    to.requests.push(from.uuid);
    this.store.save();
    return 'sent';
  }

  accept(me, other) {
    SocialService.ensure(me);
    SocialService.ensure(other);
    if (!me.requests.includes(other.uuid)) throw new Error(`${other.name} hasn't sent you a request.`);
    me.requests = me.requests.filter((u) => u !== other.uuid);
    if (!me.friends.includes(other.uuid)) me.friends.push(other.uuid);
    if (!other.friends.includes(me.uuid)) other.friends.push(me.uuid);
    other.requests = other.requests.filter((u) => u !== me.uuid);
    this.store.save();
  }

  /** Declines their request, cancels yours, or unfriends — whichever applies. */
  remove(me, other) {
    SocialService.ensure(me);
    SocialService.ensure(other);
    me.requests = me.requests.filter((u) => u !== other.uuid);
    other.requests = other.requests.filter((u) => u !== me.uuid);
    me.friends = me.friends.filter((u) => u !== other.uuid);
    other.friends = other.friends.filter((u) => u !== me.uuid);
    this.store.save();
  }

  outgoing(me) {
    return Object.values(this.store.players).filter((p) => Array.isArray(p.requests) && p.requests.includes(me.uuid));
  }

  // ---------- messages ----------

  send(from, to, text) {
    const body = String(text || '').replace(/[\u0000-\u001f§]/g, '').trim().slice(0, 256);
    if (!body) throw new Error('Type a message first.');
    if (!this.areFriends(from, to)) throw new Error(`You can only message friends. Add ${to.name} first.`);
    const last = this.lastSent.get(from.uuid) || 0;
    if (Date.now() - last < 600) throw new Error('Slow down a little.');
    this.lastSent.set(from.uuid, Date.now());
    const msg = { id: this.store.data.nextMessageId++, from: from.uuid, to: to.uuid, text: body, at: new Date().toISOString() };
    const log = this.store.data.messages;
    log.push(msg);
    if (log.length > MAX_MESSAGES) log.splice(0, log.length - MAX_MESSAGES);
    this.store.save();
    return msg;
  }

  /** Messages to or from `me` newer than `since` (message id), oldest first. */
  messagesFor(me, since = 0, withUuid = null, limit = 200) {
    return this.store.data.messages
      .filter((m) => m.id > since && (m.to === me.uuid || m.from === me.uuid)
        && (!withUuid || m.to === withUuid || m.from === withUuid))
      .slice(-limit);
  }

  // ---------- emotes ----------

  emote(player, id) {
    if (!EMOTES.includes(id)) throw new Error('Unknown emote.');
    player.emote = { id, at: Date.now() };
    // Not saved to disk on purpose: emotes only matter for a few seconds.
  }

  /** { id, ageMs } while an emote is playing, else null. */
  currentEmote(player) {
    const e = player.emote;
    if (!e) return null;
    const age = Date.now() - e.at;
    return age < EMOTE_MS ? { id: e.id, ageMs: age } : null;
  }
}

module.exports = { SocialService, EMOTES };
