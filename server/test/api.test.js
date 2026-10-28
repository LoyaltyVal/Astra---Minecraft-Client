'use strict';

// Run with: npm test   (uses Node's built-in test runner; no dependencies)
const { test, before, after } = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { App } = require('../src/App');

const ROOT = path.join(__dirname, '..');
const OWNER = { id: '0123456789abcdef0123456789abcdef', name: 'AstraOwner' };
const ALICE = { id: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', name: 'Alice' };
const BOB = { id: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', name: 'Bob' };
const PNG = fs.readFileSync(path.join(ROOT, 'seed', 'ember.png'));

let app;
let base;
const joined = new Map(); // serverId -> profile, simulates Mojang's session server
const discordPosts = [];   // what the Discord webhook received

/** Fake Mojang: hasJoined succeeds only for joins the "launcher" made below. */
async function fakeFetch(url, init) {
  const u = new URL(url);
  if (u.host === 'discord.com') {
    discordPosts.push(JSON.parse(init.body));
    return { status: 204, headers: { get: () => null }, json: async () => null };
  }
  if (u.host === 'sessionserver.mojang.com') {
    const p = joined.get(u.searchParams.get('serverId'));
    if (!p || p.name !== u.searchParams.get('username')) return { status: 204, json: async () => null };
    return { status: 200, json: async () => p };
  }
  if (u.host === 'api.mojang.com') {
    const name = decodeURIComponent(u.pathname.split('/').pop());
    if (name.toLowerCase() === 'bob') return { status: 200, json: async () => BOB };
    return { status: 404, json: async () => null };
  }
  throw new Error('unexpected fetch ' + url);
}

async function call(method, p, { token, body, cookie, origin } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (cookie) headers.Cookie = cookie;
  if (origin) headers.Origin = origin;
  const res = await fetch(base + p, { method, headers, body: body ? JSON.stringify(body) : undefined, redirect: 'manual' });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* html */ }
  return { status: res.status, json, headers: res.headers, text };
}

/** What the launcher does: challenge → (Mojang join) → verify. */
async function signIn(profile) {
  const { json: { serverId } } = await call('POST', '/api/auth/challenge');
  joined.set(serverId, profile);
  const r = await call('POST', '/api/auth/verify', { body: { username: profile.name, serverId } });
  assert.strictEqual(r.status, 200, JSON.stringify(r.json));
  return r.json;
}

before(async () => {
  const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'astra-test-'));
  app = new App({
    port: 0, publicUrl: 'http://127.0.0.1', owners: ['astraowner'], devAuth: false, dataDir,
    publicDir: path.join(ROOT, 'public'), seedDir: path.join(ROOT, 'seed'), secureCookies: false,
    discordWebhook: 'https://discord.com/api/webhooks/1/abc', discordInvite: 'https://discord.gg/astra'
  }, { fetchImpl: fakeFetch });
  const server = await app.listen(0);
  base = `http://127.0.0.1:${server.address().port}`;
});
after(() => app.close());

test('starter cosmetics are seeded and public', async () => {
  const r = await call('GET', '/api/cosmetics');
  assert.strictEqual(r.json.cosmetics.length, 4);
  const img = await fetch(base + r.json.cosmetics[0].image);
  assert.strictEqual(img.status, 200);
  assert.match(img.headers.get('content-type'), /image\/png/);
});

test('sign-in is verified with Mojang; a wrong name or reused challenge fails', async () => {
  const { json: { serverId } } = await call('POST', '/api/auth/challenge');
  joined.set(serverId, ALICE);
  const wrong = await call('POST', '/api/auth/verify', { body: { username: 'Mallory', serverId } });
  assert.strictEqual(wrong.status, 401);
  const reused = await call('POST', '/api/auth/verify', { body: { username: 'Alice', serverId } });
  assert.strictEqual(reused.status, 400, 'challenges are single-use');
});

test('config owner becomes Owner; new players are Player', async () => {
  const owner = await signIn(OWNER);
  assert.strictEqual(owner.player.rank.id, 'owner');
  assert.ok(owner.player.permissions.includes('cosmetics.manage'));
  const alice = await signIn(ALICE);
  assert.strictEqual(alice.player.rank.id, 'player');
  assert.deepStrictEqual(alice.player.permissions, []);
});

test('players equip what they own; everyone sees it in the batch lookup', async () => {
  const alice = await signIn(ALICE);
  const cape = alice.player.owned.find((c) => c.type === 'cape');
  const eq = await call('POST', '/api/me/equip', { token: alice.token, body: { type: 'cape', id: cape.id } });
  assert.strictEqual(eq.status, 200);
  const look = await call('GET', `/api/players?uuids=${ALICE.id},${'c'.repeat(32)}`);
  assert.strictEqual(look.json.players[ALICE.id].cape, cape.id);
  assert.strictEqual(Object.keys(look.json.players).length, 1, 'non-Astra players are left out');
});

test('admin upload, grant, equip, rank and ban flow with permission checks', async () => {
  const owner = await signIn(OWNER);
  const alice = await signIn(ALICE);
  const dataUrl = 'data:image/png;base64,' + PNG.toString('base64');

  // A plain player can't upload.
  const denied = await call('POST', '/api/admin/cosmetics', { token: alice.token, body: { name: 'X', type: 'cape', image: dataUrl } });
  assert.strictEqual(denied.status, 403);

  const up = await call('POST', '/api/admin/cosmetics', { token: owner.token, body: { name: 'Staff Cape', type: 'cape', rarity: 'legendary', image: dataUrl } });
  assert.strictEqual(up.status, 201, JSON.stringify(up.json));
  const id = up.json.cosmetic.id;

  // Owners can wear anything they upload straight away.
  const ownerWears = await call('POST', '/api/me/equip', { token: owner.token, body: { type: 'cape', id } });
  assert.strictEqual(ownerWears.status, 200);

  // Alice can't equip it until it's given to her.
  const notOwned = await call('POST', '/api/me/equip', { token: alice.token, body: { type: 'cape', id } });
  assert.strictEqual(notOwned.status, 400);
  const grant = await call('POST', `/api/admin/players/${ALICE.id}/cosmetics`, { token: owner.token, body: { id } });
  assert.strictEqual(grant.status, 200);
  const ok = await call('POST', '/api/me/equip', { token: alice.token, body: { type: 'cape', id } });
  assert.strictEqual(ok.status, 200);

  // Ranks: owner makes Alice a moderator; a moderator can't promote anyone.
  const mod = await call('POST', `/api/admin/players/${ALICE.id}/rank`, { token: owner.token, body: { rank: 'moderator' } });
  assert.strictEqual(mod.json.player.rank.id, 'moderator');
  const team = await call('GET', '/api/team');
  assert.ok(team.json.team.some((p) => p.name === 'Alice'));
  const modTries = await call('POST', `/api/admin/players/${OWNER.id}/rank`, { token: alice.token, body: { rank: 'player' } });
  assert.strictEqual(modTries.status, 403);

  // Add Bob by name (Mojang lookup), then the moderator bans him.
  const bob = await call('POST', '/api/admin/players', { token: owner.token, body: { name: 'bob' } });
  assert.strictEqual(bob.json.player.name, 'Bob');
  const ban = await call('POST', `/api/admin/players/${BOB.id}/ban`, { token: alice.token, body: { banned: true } });
  assert.strictEqual(ban.json.player.banned, true);
  const bobSignIn = await call('POST', '/api/auth/challenge');
  joined.set(bobSignIn.json.serverId, BOB);
  const blocked = await call('POST', '/api/auth/verify', { body: { username: 'Bob', serverId: bobSignIn.json.serverId } });
  assert.strictEqual(blocked.status, 403);

  // The last owner can't be demoted, and deleting a cosmetic unequips it.
  const self = await call('POST', `/api/admin/players/${OWNER.id}/rank`, { token: owner.token, body: { rank: 'admin' } });
  assert.strictEqual(self.status, 403);
  await call('DELETE', `/api/admin/cosmetics/${id}`, { token: owner.token });
  const after = await call('GET', `/api/players?uuids=${ALICE.id}`);
  assert.strictEqual(after.json.players[ALICE.id].cape, null);

  const log = await call('GET', '/api/admin/audit', { token: owner.token });
  assert.ok(log.json.entries.some((e) => e.action === 'banned' && e.target === 'Bob'));
});

test('website sign-in: one-time code → cookie; cross-site POSTs are blocked', async () => {
  const owner = await signIn(OWNER);
  const { json: { url } } = await call('POST', '/api/auth/web-code', { token: owner.token });
  const code = new URL(url).searchParams.get('code');
  const login = await call('GET', `/login?code=${code}`);
  assert.strictEqual(login.status, 302);
  assert.strictEqual(login.headers.get('location'), '/admin');
  const cookie = login.headers.get('set-cookie').split(';')[0];
  assert.match(login.headers.get('set-cookie'), /HttpOnly/);

  const again = await call('GET', `/login?code=${code}`);
  assert.match(again.headers.get('location'), /expired/, 'codes are single-use');

  const whoami = await call('GET', '/api/admin/whoami', { cookie });
  assert.strictEqual(whoami.json.player.name, 'AstraOwner');

  const evil = await call('PATCH', '/api/admin/cosmetics/x', { cookie, origin: 'https://evil.example', body: { name: 'pwned' } });
  assert.strictEqual(evil.status, 403);
  const host = new URL(base).host;
  const fine = await call('GET', '/api/admin/cosmetics', { cookie, origin: `http://${host}` });
  assert.strictEqual(fine.status, 200);
});

test('pages and assets are served; path traversal is refused', async () => {
  for (const p of ['/', '/cosmetics', '/team', '/players', '/admin', '/player/Alice']) {
    const r = await fetch(base + p);
    assert.strictEqual(r.status, 200, p);
    assert.match(r.headers.get('content-type'), /text\/html/);
  }
  const css = await fetch(base + '/assets/css/site.css');
  assert.strictEqual(css.status, 200);
  const trav = await fetch(base + '/assets/../src/config.js');
  assert.notStrictEqual(trav.status, 200);
  const trav2 = await fetch(base + '/assets/%2e%2e/%2e%2e/package.json');
  assert.notStrictEqual(trav2.status, 200);
});

test('the game reports which server you are on; IPs and LAN hosts stay private', async () => {
  const { cleanServer } = require('../src/services/Players');
  assert.strictEqual(cleanServer('Play.Hypixel.net:25565'), 'play.hypixel.net');
  assert.strictEqual(cleanServer('mc.example.com:25570'), 'mc.example.com:25570');
  assert.strictEqual(cleanServer('192.168.1.5'), 'a private server');
  assert.strictEqual(cleanServer('localhost:25565'), 'a private server');
  assert.strictEqual(cleanServer('singleplayer'), 'singleplayer');

  const alice = await signIn(ALICE);
  await call('POST', '/api/me/presence', { token: alice.token, body: { server: 'mc.hypixel.net', version: '1.8.9' } });
  const online = await call('GET', '/api/online');
  const a = online.json.players.find((p) => p.name === 'Alice');
  assert.deepStrictEqual(a.playing, { server: 'mc.hypixel.net', version: '1.8.9', hidden: false });
  const profile = await call('GET', '/api/profile/Alice');
  assert.strictEqual(profile.json.player.playing.server, 'mc.hypixel.net');

  // "Share My Server" off: still shown as playing, but not where.
  await call('POST', '/api/me/presence', { token: alice.token, body: { server: 'mc.hypixel.net', version: '1.8.9', hidden: true } });
  const hidden = await call('GET', '/api/profile/Alice');
  assert.strictEqual(hidden.json.player.playing.server, null);

  // Back in the menus: no longer listed as in game.
  await call('POST', '/api/me/presence', { token: alice.token, body: { server: null, version: null } });
  const after = await call('GET', '/api/online');
  assert.ok(!after.json.players.some((p) => p.name === 'Alice'));
});

test('Discord: invite on the website, webhook posts staff actions', async () => {
  const meta = await call('GET', '/api/meta');
  assert.strictEqual(meta.json.discordInvite, 'https://discord.gg/astra');
  await new Promise((r) => setTimeout(r, 1500)); // the webhook queue sends one message at a time
  const text = JSON.stringify(discordPosts);
  assert.match(text, /joined Astra/);
  assert.match(text, /uploaded cosmetic/);
});

test('friends: request, accept, message, remove; emotes show in the batch lookup', async () => {
  const alice = await signIn(ALICE);
  const owner = await signIn(OWNER);
  const req1 = await call('POST', '/api/friends/request', { token: alice.token, body: { name: 'AstraOwner' } });
  assert.strictEqual(req1.json.result, 'sent');
  const blocked = await call('POST', '/api/friends/messages', { token: alice.token, body: { to: 'AstraOwner', text: 'hi' } });
  assert.strictEqual(blocked.status, 400, 'only friends can message');
  const list = await call('GET', '/api/friends', { token: owner.token });
  assert.strictEqual(list.json.incoming[0].name, 'Alice');
  const acc = await call('POST', '/api/friends/accept', { token: owner.token, body: { uuid: ALICE.id } });
  assert.strictEqual(acc.status, 200);
  const sent = await call('POST', '/api/friends/messages', { token: alice.token, body: { to: 'AstraOwner', text: 'gg §cwp' } });
  assert.strictEqual(sent.json.message.text, 'gg cwp', 'colour codes are stripped');
  const inbox = await call('GET', '/api/friends/messages?since=0', { token: owner.token });
  assert.strictEqual(inbox.json.messages.at(-1).fromName, 'Alice');
  const unknown = await call('POST', '/api/friends/request', { token: alice.token, body: { name: 'Nobody' } });
  assert.strictEqual(unknown.status, 404);

  await call('POST', '/api/me/emote', { token: alice.token, body: { emote: 'wave' } });
  const look = await call('GET', `/api/players?uuids=${ALICE.id}`);
  assert.strictEqual(look.json.players[ALICE.id].emote.id, 'wave');

  await call('POST', '/api/friends/remove', { token: owner.token, body: { uuid: ALICE.id } });
  const after = await call('GET', '/api/friends', { token: alice.token });
  assert.strictEqual(after.json.friends.length, 0);
});

test('account settings: bio, hide server, friend requests off; Discord link needs config', async () => {
  const alice = await signIn(ALICE);
  const owner = await signIn(OWNER);
  const saved = await call('POST', '/api/me/settings', { token: alice.token, body: { bio: '  <b>bedwars</b> main  ', hideServer: true, friendRequests: 'off' } });
  assert.strictEqual(saved.json.player.settings.bio, 'b bedwars /b main');
  const profile = await call('GET', '/api/profile/Alice');
  assert.strictEqual(profile.json.player.bio, 'b bedwars /b main');
  await call('POST', '/api/me/presence', { token: alice.token, body: { server: 'mc.hypixel.net', version: '1.8.9' } });
  const online = await call('GET', '/api/online');
  const me = online.json.players.find((p) => p.name === 'Alice');
  assert.strictEqual(me.playing.server, null, 'hidden by the account setting');
  const req = await call('POST', '/api/friends/request', { token: owner.token, body: { name: 'Alice' } });
  assert.strictEqual(req.status, 400);
  const settings = await call('GET', '/api/me/settings', { token: alice.token });
  assert.strictEqual(settings.json.discordAvailable, false);
  const link = await call('GET', '/api/discord/link', { token: alice.token });
  assert.strictEqual(link.status, 503);
  await call('POST', '/api/me/settings', { token: alice.token, body: { hideServer: false, friendRequests: 'on', bio: '' } });
});

test('player uploads: pending is hidden and unwearable, staff approve or reject', async () => {
  const alice = await signIn({ id: 'cccccccccccccccccccccccccccccccc', name: 'Carl' });
  const owner = await signIn(OWNER);
  const image = 'data:image/png;base64,' + PNG.toString('base64');
  const up = await call('POST', '/api/me/uploads', { token: alice.token, body: { name: 'My Cape', type: 'cape', image } });
  assert.strictEqual(up.status, 201);
  assert.strictEqual(up.json.upload.status, 'pending');
  const id = up.json.upload.id;
  const catalog = await call('GET', '/api/cosmetics');
  assert.ok(!catalog.json.cosmetics.some((c) => c.id === id), 'pending uploads stay out of the catalog');
  const wear = await call('POST', '/api/me/equip', { token: alice.token, body: { type: 'cape', id } });
  assert.strictEqual(wear.status, 400, 'pending uploads cannot be worn');
  const tooSoon = await call('POST', '/api/me/uploads', { token: alice.token, body: { name: 'Again', type: 'wings', image } });
  assert.strictEqual(tooSoon.status, 429);

  const notStaff = await call('GET', '/api/admin/uploads', { token: alice.token });
  assert.strictEqual(notStaff.status, 403);
  const queue = await call('GET', '/api/admin/uploads', { token: owner.token });
  assert.strictEqual(queue.json.uploads[0].uploader.name, 'Carl');
  const ok = await call('POST', `/api/admin/uploads/${id}/approve`, { token: owner.token });
  assert.strictEqual(ok.json.upload.status, 'approved');
  const me = await call('GET', '/api/me', { token: alice.token });
  assert.strictEqual(me.json.player.equippedIds.cape, id, 'approved upload is worn straight away');
  const catalog2 = await call('GET', '/api/cosmetics');
  assert.ok(!catalog2.json.cosmetics.some((c) => c.id === id), 'approved uploads stay private to the uploader');

  const mine = await call('GET', '/api/me/uploads', { token: alice.token });
  assert.strictEqual(mine.json.uploads[0].status, 'approved');
  const del = await call('DELETE', `/api/me/uploads/${id}`, { token: alice.token });
  assert.strictEqual(del.status, 200);
  const me2 = await call('GET', '/api/me', { token: alice.token });
  assert.strictEqual(me2.json.player.equippedIds.cape, null);
});

test('live WebSocket: messages, friend changes and emotes arrive instantly', async () => {
  const dave = await signIn({ id: 'dddddddddddddddddddddddddddddddd', name: 'Dave' });
  const erin = await signIn({ id: 'eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee', name: 'Erin' });
  const url = base.replace(/^http/, 'ws') + '/api/live';

  const noAuth = new WebSocket(url, ['astra']);
  const refused = await new Promise((r) => { noAuth.onerror = () => r(true); noAuth.onopen = () => r(false); });
  assert.ok(refused, 'needs a session');

  const ws = new WebSocket(url, ['astra', `astra-auth.${dave.token}`]);
  const got = [];
  let wake = null;
  ws.onmessage = (e) => { got.push(JSON.parse(e.data)); if (wake) wake(); };
  const next = (type) => new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error(`no ${type} event`)), 3000);
    const check = () => {
      const i = got.findIndex((m) => m.type === type);
      if (i >= 0) { clearTimeout(t); wake = null; resolve(got.splice(i, 1)[0]); } else wake = check;
    };
    check();
  });
  assert.strictEqual((await next('hello')).uuid, 'dddddddddddddddddddddddddddddddd');
  assert.strictEqual(ws.protocol, 'astra');

  await call('POST', '/api/friends/request', { token: erin.token, body: { name: 'Dave' } });
  await next('friends');
  await call('POST', '/api/friends/accept', { token: dave.token, body: { uuid: 'eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee' } });
  await next('friends');
  await call('POST', '/api/friends/messages', { token: erin.token, body: { to: 'Dave', text: 'live?' } });
  const m = await next('message');
  assert.strictEqual(m.message.text, 'live?');
  assert.strictEqual(m.message.fromName, 'Erin');
  await call('POST', '/api/me/presence', { token: erin.token, body: { server: 'mc.hypixel.net', version: '1.8.9' } });
  await next('friends');
  await call('POST', '/api/me/emote', { token: erin.token, body: { emote: 'dab' } });
  const e = await next('emote');
  assert.deepStrictEqual([e.uuid, e.id], ['eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee', 'dab']);
  ws.close();
});

test('radio stations are served (built-in list by default)', async () => {
  const r = await call('GET', '/api/radio');
  assert.strictEqual(r.status, 200);
  assert.ok(r.json.stations.length >= 5);
  assert.ok(r.json.stations.every((s) => /^https:\/\//.test(s.url) && s.name));
});
