/* global astra */
'use strict';

const $ = (id) => document.getElementById(id);
const MAX_LOG_LINES = 4000;

const state = {
  versions: [],
  selected: '1.8.9',
  account: null,
  phase: 'idle', // idle | preparing | running
  logLines: [],
  cosmeticType: 'cape',
  astra: null,        // your Astra account (rank, owned cosmetics, equipped), once loaded
  astraError: null,
  astraLoading: false
};

// ---------------------------------------------------------------- helpers

function toast(message, ok = false) {
  const el = $('toast');
  el.textContent = message;
  el.classList.toggle('ok', ok);
  el.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => (el.hidden = true), ok ? 3000 : 6000);
}

function setStatus(text, isError = false) {
  const el = $('status');
  el.textContent = text;
  el.classList.toggle('error', isError);
}

function appendLog(line) {
  state.logLines.push(line);
  if (state.logLines.length > MAX_LOG_LINES) {
    state.logLines.splice(0, state.logLines.length - MAX_LOG_LINES);
  }
  const el = $('logs');
  const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 40;
  el.textContent = state.logLines.join('\n');
  if (atBottom) el.scrollTop = el.scrollHeight;
}

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

// ---------------------------------------------------------------- navigation

function showView(name) {
  document.querySelectorAll('.nav').forEach((b) => b.classList.toggle('active', b.dataset.view === name));
  document.querySelectorAll('.view').forEach((v) => v.classList.toggle('active', v.id === `view-${name}`));
  if (name === 'logs') {
    const logs = $('logs');
    logs.scrollTop = logs.scrollHeight;
  }
  if (name === 'friends') loadFriends();
  if (name === 'cosmetics') {
    if (!state.astra) loadAstra();
    else renderCosmetics();
  }
}

document.querySelectorAll('.nav[data-view]').forEach((btn) => {
  btn.addEventListener('click', () => showView(btn.dataset.view));
});

$('win-min').addEventListener('click', () => astra.window.minimize());
$('win-max').addEventListener('click', () => astra.window.maximize());
$('win-close').addEventListener('click', () => astra.window.close());

// ---------------------------------------------------------------- versions

/** Built versions grouped by family ("1.21" holds 1.21.11, 1.21.4, ...), in the order given (newest first). */
function versionFamilies() {
  const families = [];
  for (const v of state.versions) {
    let fam = families.find((f) => f.family === v.family);
    if (!fam) families.push((fam = { family: v.family, subtitle: v.subtitle, versions: [] }));
    fam.versions.push(v);
  }
  return families;
}

function selectVersion(id) {
  if (state.phase !== 'idle') return;
  state.selected = id;
  astra.saveSettings({ lastVersion: id });
  renderVersions();
}

function renderVersions() {
  const root = $('versions');
  root.innerHTML = '';
  const families = versionFamilies();
  root.classList.toggle('many', families.length > 3);
  if (!families.length) {
    root.append(el('p', 'versions-empty', 'No Astra builds yet. Run Start Astra.bat to build them.'));
    return;
  }
  for (const fam of families) {
    const chosen = fam.versions.find((v) => v.id === state.selected);
    const shown = chosen || fam.versions[0];
    const btn = el('div', 'version');
    btn.tabIndex = 0;
    btn.setAttribute('role', 'radio');
    btn.setAttribute('aria-checked', String(Boolean(chosen)));
    btn.setAttribute('aria-label', `Minecraft ${shown.id}`);
    const top = el('div', 'version-top');
    top.append(el('span', 'version-name', fam.versions.length > 1 ? fam.family : shown.id), el('span', 'version-dot'));
    btn.append(top, el('div', 'version-sub', fam.subtitle));
    if (fam.versions.length > 1) {
      // Several releases of this line are built: pick the exact one.
      const pick = el('select', 'version-patch');
      pick.setAttribute('aria-label', `Minecraft ${fam.family} release`);
      for (const v of fam.versions) {
        const o = el('option', null, v.id);
        o.value = v.id;
        o.selected = v.id === shown.id;
        pick.append(o);
      }
      pick.addEventListener('click', (e) => e.stopPropagation());
      pick.addEventListener('change', () => selectVersion(pick.value));
      pick.disabled = state.phase !== 'idle';
      btn.append(pick);
    }
    btn.addEventListener('click', () => selectVersion(shown.id));
    btn.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        selectVersion(shown.id);
      }
    });
    root.appendChild(btn);
  }
}

// ---------------------------------------------------------------- account

function renderAccount() {
  const head = $('account-head');
  const placeholder = $('account-placeholder');
  if (state.account) {
    $('account-name').textContent = state.account.name;
    $('account-action').textContent = 'Switch account ▾';
    head.onerror = () => {
      head.hidden = true;
      placeholder.hidden = false;
    };
    head.onload = () => {
      head.hidden = false;
      placeholder.hidden = true;
    };
    head.src = `https://mc-heads.net/avatar/${encodeURIComponent(state.account.id)}/80`;
  } else {
    $('account-name').textContent = 'Not signed in';
    $('account-action').textContent = 'Sign in with Microsoft';
    head.removeAttribute('src');
    head.hidden = true;
    placeholder.hidden = false;
  }
  renderLaunch();
}

async function signIn() {
  setStatus('Waiting for Microsoft sign-in…');
  const res = await astra.login();
  if (res.ok) {
    accountChanged(res.value, `Signed in as ${res.value.name}`);
  } else if (res.cancelled) {
    setStatus('Ready');
  } else {
    setStatus(res.error, true);
    toast(res.error);
  }
  renderAccount();
}

/** After switching or removing an account: reload everything that belongs to the account. */
function accountChanged(account, message) {
  state.account = account || null;
  state.astra = null;
  state.astraError = null;
  renderAstraAccount();
  renderAccount();
  setStatus(message);
  if (state.account) loadAstra();
  renderAccountsList();
  connectLive();
}

function closeAccountMenu() {
  $('account-menu').hidden = true;
  $('account-action').setAttribute('aria-expanded', 'false');
}

async function openAccountMenu() {
  const menu = $('account-menu');
  const res = await astra.accounts();
  const accounts = res.ok ? res.value : [];
  menu.replaceChildren();
  for (const a of accounts) {
    const row = el('button', 'acct-row');
    row.setAttribute('role', 'menuitem');
    const img = document.createElement('img');
    img.src = `https://mc-heads.net/avatar/${encodeURIComponent(a.id)}/56`;
    img.alt = '';
    img.onerror = () => { img.style.visibility = 'hidden'; };
    row.append(img, el('span', 'acct-name', a.name));
    if (a.expired) row.append(el('span', 'acct-tag expired', 'Sign in again'));
    else if (a.active) row.append(el('span', 'acct-tag', 'Playing'));
    const remove = el('span', 'acct-remove', '✕');
    remove.title = `Remove ${a.name} from this launcher`;
    remove.addEventListener('click', async (e) => {
      e.stopPropagation();
      if (!confirm(`Remove ${a.name} from the launcher? You can add it again later.`)) return;
      const r = await astra.removeAccount(a.id);
      if (!r.ok) return toast(r.error);
      if (a.active) accountChanged(r.value, r.value ? `Switched to ${r.value.name}` : 'Signed out');
      openAccountMenu();
    });
    row.append(remove);
    row.addEventListener('click', async () => {
      closeAccountMenu();
      if (a.expired) return signIn();
      if (a.active) return;
      const r = await astra.switchAccount(a.id);
      if (!r.ok) return toast(r.error);
      accountChanged(r.value, `Switched to ${r.value.name}`);
      toast(`Playing as ${r.value.name}`, true);
    });
    menu.appendChild(row);
  }
  menu.appendChild(el('div', 'acct-sep'));
  const add = el('button', 'acct-row plain', '+ Add another account');
  add.addEventListener('click', () => {
    closeAccountMenu();
    signIn();
  });
  const out = el('button', 'acct-row plain', `Sign out of ${state.account.name}`);
  out.addEventListener('click', async () => {
    closeAccountMenu();
    const r = await astra.logout();
    accountChanged(r.ok ? r.value : null, r.ok && r.value ? `Signed out. Switched to ${r.value.name}` : 'Signed out');
  });
  menu.append(add, out);
  menu.hidden = false;
  $('account-action').setAttribute('aria-expanded', 'true');
  const first = menu.querySelector('.acct-row');
  if (first) first.focus();
}

// Arrow keys move through the account menu.
$('account-menu').addEventListener('keydown', (e) => {
  if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
  e.preventDefault();
  const rows = [...$('account-menu').querySelectorAll('.acct-row')];
  const i = rows.indexOf(document.activeElement);
  const next = rows[(i + (e.key === 'ArrowDown' ? 1 : rows.length - 1)) % rows.length];
  if (next) next.focus();
});

// ---------------------------------------------------------------- accounts (Settings)

async function renderAccountsList() {
  const box = $('accounts-list');
  const res = await astra.accounts();
  const accounts = res.ok ? res.value : [];
  box.replaceChildren();
  if (!accounts.length) {
    box.appendChild(el('p', 'hint', 'No accounts yet. Add your Microsoft account to play.'));
    return;
  }
  for (const a of accounts) {
    const line = el('div', `acct-line${a.active ? ' active' : ''}${a.expired ? ' expired' : ''}`);
    const img = document.createElement('img');
    img.src = `https://mc-heads.net/avatar/${encodeURIComponent(a.id)}/68`;
    img.alt = '';
    img.onerror = () => { img.style.visibility = 'hidden'; };
    const who = el('div', 'who');
    who.append(el('b', '', a.name), el('span', '', a.expired ? 'Microsoft sign-in expired' : a.active ? 'Playing with this account' : 'Saved'));
    line.append(img, who);
    if (a.expired) {
      const b = el('button', 'primary', 'Sign in again');
      b.addEventListener('click', () => signIn());
      line.appendChild(b);
    } else if (!a.active) {
      const b = el('button', 'ghost', 'Switch');
      b.addEventListener('click', async () => {
        const r = await astra.switchAccount(a.id);
        if (!r.ok) return toast(r.error);
        accountChanged(r.value, `Switched to ${r.value.name}`);
        toast(`Playing as ${r.value.name}`, true);
      });
      line.appendChild(b);
    }
    const rm = el('button', 'ghost', 'Remove');
    rm.addEventListener('click', async () => {
      if (!confirm(`Remove ${a.name} from the launcher? You can add it again later.`)) return;
      const r = await astra.removeAccount(a.id);
      if (!r.ok) return toast(r.error);
      if (a.active) accountChanged(r.value, r.value ? `Switched to ${r.value.name}` : 'Signed out');
      else renderAccountsList();
    });
    line.appendChild(rm);
    box.appendChild(line);
  }
}

$('accounts-add').addEventListener('click', () => {
  if (state.phase !== 'idle') return toast('Close Minecraft before changing accounts.');
  signIn();
});

// ---------------------------------------------------------------- live connection (WebSocket)

/**
 * A WebSocket to the Astra server for instant friend requests and messages. Reconnects by itself
 * and reopens for the new account after a switch.
 */
const live = { ws: null, retry: null, delay: 2000, generation: 0 };

async function connectLive() {
  live.generation++;
  const gen = live.generation;
  clearTimeout(live.retry);
  if (live.ws) {
    live.ws.onclose = null;
    live.ws.close();
    live.ws = null;
  }
  if (!state.account) return;
  const res = await astra.server.live();
  if (gen !== live.generation) return;
  if (!res.ok) {
    live.retry = setTimeout(connectLive, 60000);
    return;
  }
  let ws;
  try {
    ws = new WebSocket(res.value.url, ['astra', `astra-auth.${res.value.token}`]);
  } catch {
    return;
  }
  live.ws = ws;
  ws.onopen = () => {
    live.delay = 2000;
    refreshFriendBadge();
  };
  ws.onmessage = (e) => {
    let event;
    try { event = JSON.parse(e.data); } catch { return; }
    if (event.type === 'friends') {
      if ($('view-friends').classList.contains('active')) loadFriends();
      else refreshFriendBadge();
    } else if (event.type === 'message' && event.message && state.account && event.message.to === String(state.account.id).replace(/-/g, '')) {
      toast(`${event.message.fromName}: ${event.message.text}`, true);
    }
  };
  ws.onclose = () => {
    if (gen !== live.generation) return;
    live.retry = setTimeout(connectLive, live.delay);
    live.delay = Math.min(live.delay * 2, 60000);
  };
}

/** Updates the red request count on the Friends tab without opening it. */
async function refreshFriendBadge() {
  const res = await astra.server.friends();
  if (!res.ok) return;
  const badge = $('friends-badge');
  badge.hidden = !res.value.incoming.length;
  badge.textContent = String(res.value.incoming.length);
}

$('account-action').addEventListener('click', (e) => {
  e.stopPropagation();
  if (state.phase !== 'idle') return;
  if (!state.account) return signIn();
  if ($('account-menu').hidden) openAccountMenu();
  else closeAccountMenu();
});
document.addEventListener('click', (e) => {
  if (!$('account-menu').hidden && !$('account-menu').contains(e.target)) closeAccountMenu();
});
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') closeAccountMenu();
});

// ---------------------------------------------------------------- launch

function renderLaunch(progress) {
  const btn = $('launch');
  const label = $('launch-label');
  const fill = $('launch-fill');
  btn.classList.toggle('busy', state.phase === 'preparing');
  btn.classList.toggle('running', state.phase === 'running');

  if (state.phase === 'preparing') {
    btn.disabled = true;
    const pct = progress && progress.total > 0 ? Math.round((progress.current / progress.total) * 100) : null;
    label.textContent = pct === null ? 'Preparing…' : `${pct}%`;
    fill.style.width = pct === null ? '0%' : `${pct}%`;
  } else if (state.phase === 'running') {
    btn.disabled = false;
    label.textContent = 'Playing';
    fill.style.width = '0%';
  } else {
    btn.disabled = false;
    label.textContent = state.account ? 'Launch' : 'Sign in';
    fill.style.width = '0%';
  }
}

$('launch').addEventListener('click', async () => {
  if (state.phase === 'running') {
    setStatus(`Minecraft ${state.selected} is running`);
    return;
  }
  if (state.phase !== 'idle') return;
  if (!state.account) {
    signIn();
    return;
  }
  state.phase = 'preparing';
  state.logLines = [];
  appendLog(`[Astra] Launching ${state.selected}…`);
  setStatus('Preparing…');
  renderLaunch();

  const res = await astra.launch(state.selected);
  if (!res.ok) {
    state.phase = 'idle';
    setStatus(res.error, true);
    toast(res.error);
    appendLog(`[Astra] ${res.error}`);
    renderLaunch();
  }
});

astra.onProgress((p) => {
  if (state.phase !== 'preparing') return;
  const count = p.total > 0 ? ` (${p.current}/${p.total})` : '';
  setStatus(`${p.label}${count}`);
  renderLaunch(p);
});

astra.onState(({ name, detail }) => {
  if (name === 'preparing') {
    state.phase = 'preparing';
  } else if (name === 'running') {
    state.phase = 'running';
    setStatus(`Minecraft ${state.selected} is running`);
  } else if (name === 'closed') {
    state.phase = 'idle';
    const code = detail && detail.code;
    if (code && code !== 0) {
      setStatus(`Minecraft closed with error code ${code}. See Logs.`, true);
    } else {
      setStatus('Ready');
    }
  } else if (name === 'error') {
    state.phase = 'idle';
  }
  renderLaunch();
});

astra.onLog(appendLog);

// ---------------------------------------------------------------- Astra account & cosmetics

/** Signs in to the Astra server (quietly) and loads your rank and cosmetics. */
async function loadAstra() {
  if (!state.account || state.astraLoading) return;
  state.astraLoading = true;
  renderCosmetics();
  const res = await astra.server.account();
  state.astraLoading = false;
  if (res.ok) {
    state.astra = res.value;
    state.astraError = null;
  } else {
    state.astra = null;
    state.astraError = res.error;
  }
  renderAstraAccount();
  renderCosmetics();
}

function renderAstraAccount() {
  const rank = $('account-rank');
  const a = state.account && state.astra;
  if (a && a.rank && a.rank.id !== 'player') {
    rank.textContent = a.rank.name;
    rank.style.color = a.rank.color;
    rank.hidden = false;
  } else {
    rank.hidden = true;
  }
  $('nav-admin').hidden = !(a && a.staff);
}

function renderCosmetics() {
  const type = state.cosmeticType;
  document.querySelectorAll('.seg').forEach((b) => {
    const active = b.dataset.type === type;
    b.classList.toggle('active', active);
    b.setAttribute('aria-selected', String(active));
  });
  const grid = $('cos-grid');
  const hint = $('cos-hint');
  grid.innerHTML = '';
  hint.textContent = '';

  const message = (title, body) => {
    const empty = el('div', 'cos-empty');
    empty.append(el('b', '', title), document.createElement('br'), document.createTextNode(body));
    grid.appendChild(empty);
  };
  if (!state.account) return message('Sign in first.', 'Sign in with Microsoft on the Play tab to see your cosmetics.');
  if (state.astraError) return message("Couldn't reach your Astra account.", `${state.astraError} Check the Astra server address in Settings, then click Refresh.`);
  if (!state.astra) return message('Loading your cosmetics…', 'Signing in to the Astra server.');

  hint.textContent = type === 'cape'
    ? 'Click a cape to wear it. Staff can give you special ones from the admin panel.'
    : 'Click wings to wear them. Size and flap speed are in game: Right Shift → Cosmetics → Options.';
  const items = state.astra.owned.filter((c) => c.type === type);
  if (!items.length) return message(type === 'cape' ? 'No capes yet.' : 'No wings yet.', 'Free cosmetics and ones given to you show up here.');

  for (const item of items) {
    const on = state.astra.equipped[type] === item.id;
    const card = el('button', 'cos' + (on ? ' equipped' : ''));
    card.title = on ? 'Click to take off' : 'Click to wear';
    const thumb = el('div', 'cos-thumb' + (type === 'cape' ? ' cape' : ''));
    if (item.image) {
      const img = document.createElement('img');
      img.src = item.image;
      img.alt = '';
      thumb.appendChild(img);
    } else {
      thumb.appendChild(el('span', 'cos-state', "Couldn't load image"));
    }
    card.append(thumb, el('span', 'cos-name', item.name), el('span', `cos-rarity ${item.rarity}`, item.rarity),
      el('span', 'cos-state', on ? 'Wearing' : 'Click to wear'));
    card.addEventListener('click', () => equipCosmetic(on ? null : item.id));
    grid.appendChild(card);
  }
}

async function equipCosmetic(id) {
  const type = state.cosmeticType;
  const before = state.astra.equipped[type];
  state.astra.equipped[type] = id;
  renderCosmetics();
  const res = await astra.server.equip(type, id);
  if (!res.ok) {
    state.astra.equipped[type] = before;
    renderCosmetics();
    toast(res.error);
  } else {
    state.astra.equipped = res.value;
    toast(id ? 'Wearing it now. Everyone on Astra will see it.' : 'Taken off.', true);
  }
}

async function openWebsite() {
  const res = await astra.server.openWebsite(null);
  if (!res.ok) toast(res.error);
}

document.querySelectorAll('.seg').forEach((b) => {
  b.addEventListener('click', () => {
    state.cosmeticType = b.dataset.type;
    renderCosmetics();
  });
});
$('cos-refresh').addEventListener('click', loadAstra);
$('cos-web').addEventListener('click', openWebsite);
$('cos-upload').addEventListener('click', async () => {
  const res = await astra.server.openWebsite('/account#uploads');
  if (!res.ok) toast(res.error);
});
$('nav-admin').addEventListener('click', openWebsite);

// ---------------------------------------------------------------- friends view

function statusOf(f) {
  if (f.playing) {
    if (!f.playing.server) return `In game · ${f.playing.version}`;
    return f.playing.server === 'singleplayer' ? 'Playing singleplayer' : `On ${f.playing.server}`;
  }
  return f.inClient ? 'In the menus' : f.online ? 'Online' : 'Offline';
}

function friendCard(f, buttons) {
  const card = el('div', 'friend' + (f.playing ? ' playing' : ''));
  const img = document.createElement('img');
  img.src = `https://mc-heads.net/avatar/${f.uuid}/76`;
  img.alt = '';
  img.onerror = () => { img.style.visibility = 'hidden'; };
  const who = el('div', 'who');
  const name = el('b', '', f.name);
  if (f.rank && f.rank.color) name.style.color = f.rank.color;
  who.append(name, el('span', '', statusOf(f)));
  const acts = el('div', 'acts');
  for (const [label, cls, fn] of buttons) {
    const b = el('button', cls, label);
    b.addEventListener('click', fn);
    acts.appendChild(b);
  }
  card.append(img, who, acts);
  return card;
}

async function friendAction(action, body, done) {
  const res = await astra.server.friendAction(action, body);
  if (!res.ok) return toast(res.error);
  if (done) toast(done, true);
  renderFriends(res.value);
}

async function loadFriends() {
  const list = $('friend-list');
  if (!state.account) {
    list.replaceChildren(el('div', 'friend-empty', 'Sign in with Microsoft on the Play tab to see your friends.'));
    return;
  }
  const res = await astra.server.friends();
  if (!res.ok) {
    list.replaceChildren(el('div', 'friend-empty', `Couldn't load friends: ${res.error}`));
    return;
  }
  renderFriends(res.value);
}

function renderFriends(data) {
  // Everyone in game on Astra (the list below is only your friends).
  const pill = $('friends-online');
  pill.hidden = typeof data.online !== 'number';
  if (!pill.hidden) pill.textContent = `${data.online.toLocaleString()} on Astra`;
  const reqBox = $('friend-requests');
  reqBox.replaceChildren();
  const badge = $('friends-badge');
  badge.hidden = !data.incoming.length;
  badge.textContent = String(data.incoming.length);
  if (data.incoming.length || data.outgoing.length) {
    const box = el('div', 'friend-requests-box');
    box.appendChild(el('div', 'friend-section', 'Requests'));
    const grid = el('div', 'friend-list');
    for (const f of data.incoming) {
      const card = friendCard({ ...f, playing: null, online: false }, [
        ['Accept', 'primary', () => friendAction('accept', { uuid: f.uuid }, `You and ${f.name} are now friends.`)],
        ['Decline', 'ghost', () => friendAction('remove', { uuid: f.uuid })]
      ]);
      card.querySelector('.who span').textContent = 'Wants to be friends';
      grid.appendChild(card);
    }
    for (const f of data.outgoing) {
      const card = friendCard({ ...f, playing: null, online: false }, [
        ['Cancel', 'ghost', () => friendAction('remove', { uuid: f.uuid })]
      ]);
      card.querySelector('.who span').textContent = 'Request sent';
      grid.appendChild(card);
    }
    box.appendChild(grid);
    reqBox.appendChild(box);
  }
  const list = $('friend-list');
  list.replaceChildren();
  if (!data.friends.length) {
    list.appendChild(el('div', 'friend-empty', 'No friends yet. Add someone by their Minecraft name above. They need to have launched Astra once.'));
    return;
  }
  const online = data.friends.filter((f) => f.playing).length;
  const head = el('div', 'friend-section', `Friends · ${online} in game`);
  head.style.gridColumn = '1 / -1';
  list.appendChild(head);
  for (const f of data.friends) {
    list.appendChild(friendCard(f, [
      ['Remove', 'ghost', () => {
        if (confirm(`Remove ${f.name} from your friends?`)) friendAction('remove', { uuid: f.uuid });
      }]
    ]));
  }
}

$('friend-add').addEventListener('submit', (e) => {
  e.preventDefault();
  const name = $('friend-name').value.trim();
  if (!/^[A-Za-z0-9_]{1,16}$/.test(name)) return toast('Type a Minecraft name.');
  $('friend-name').value = '';
  friendAction('request', { name }, `Friend request sent to ${name}.`);
});
$('friends-refresh').addEventListener('click', loadFriends);
$('friends-web').addEventListener('click', async () => {
  const res = await astra.server.openWebsite('/friends');
  if (!res.ok) toast(res.error);
});

// ---------------------------------------------------------------- logs view

$('logs-copy').addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText(state.logLines.join('\n'));
    toast('Logs copied', true);
  } catch {
    toast('Could not copy the logs.');
  }
});
$('logs-clear').addEventListener('click', () => {
  state.logLines = [];
  $('logs').textContent = '';
});
$('logs-open').addEventListener('click', () => astra.openLogs());

// ---------------------------------------------------------------- settings

function bindSettings(settings) {
  const memory = $('memory');
  memory.value = settings.memoryGb;
  $('memory-value').textContent = `${settings.memoryGb} GB`;
  memory.addEventListener('input', () => ($('memory-value').textContent = `${memory.value} GB`));
  memory.addEventListener('change', () => astra.saveSettings({ memoryGb: Number(memory.value) }));

  const hide = $('hide-on-launch');
  hide.checked = settings.hideOnLaunch;
  hide.addEventListener('change', () => astra.saveSettings({ hideOnLaunch: hide.checked }));

  const share = $('share-minecraft');
  share.checked = settings.shareMinecraft;
  share.addEventListener('change', () => astra.saveSettings({ shareMinecraft: share.checked }));

  const discordStatus = $('discord-status');
  discordStatus.checked = settings.discordStatus;
  discordStatus.addEventListener('change', () => astra.saveSettings({ discordStatus: discordStatus.checked }));

  const server = $('server-url');
  server.value = settings.serverUrl;
  server.addEventListener('change', async () => {
    const res = await astra.saveSettings({ serverUrl: server.value });
    if (!res.ok) {
      toast(res.error);
      return;
    }
    server.value = res.value.serverUrl;
    state.astra = null;
    state.astraError = null;
    renderAstraAccount();
    loadAstra();
    connectLive();
  });

  const java = $('java-path');
  java.value = settings.javaPath;
  java.addEventListener('change', () => astra.saveSettings({ javaPath: java.value }));

  $('open-folder').addEventListener('click', () => astra.openFolder(state.selected));
}

// ---------------------------------------------------------------- start

// The in-game account switcher switched accounts: follow along.
astra.onAccountChanged((account) => {
  if (account && (!state.account || account.id !== state.account.id)) {
    accountChanged(account, `Switched to ${account.name} in game`);
  } else {
    renderAccountsList();
  }
});

(async function init() {
  const [info, settings, account] = await Promise.all([astra.info(), astra.settings(), astra.account()]);

  if (info.ok) {
    state.versions = info.value.versions;
    const ids = state.versions.map((v) => v.id);
    if (ids.length) {
      $('hero-versions').textContent =
        ids.length === 1
          ? `PvP client for Minecraft ${ids[0]}`
          : ids.length === 2
          ? `PvP client for Minecraft ${ids[0]} & ${ids[1]}`
          : `PvP client for Minecraft ${ids[ids.length - 1]} to ${ids[0]}`;
    }
    $('app-version').textContent = `Astra Launcher ${info.value.version}`;
    $('game-folder').textContent = `${info.value.gameFolder}  (one folder per version)`;
    if (info.value.running) state.phase = 'running';
  }
  if (settings.ok) {
    if (state.versions.some((v) => v.id === settings.value.lastVersion)) {
      state.selected = settings.value.lastVersion;
    } else if (state.versions.length && !state.versions.some((v) => v.id === state.selected)) {
      state.selected = state.versions[0].id;
    }
    bindSettings(settings.value);
  }
  if (account.ok) {
    state.account = account.value;
  }

  renderVersions();
  renderAccount();
  loadAstra();
  renderAccountsList();
  connectLive();
})();
