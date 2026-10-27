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

function renderVersions() {
  const root = $('versions');
  root.innerHTML = '';
  for (const v of state.versions) {
    const btn = el('button', 'version');
    btn.setAttribute('role', 'radio');
    btn.setAttribute('aria-checked', String(v.id === state.selected));
    const top = el('div', 'version-top');
    top.append(el('span', 'version-name', v.title), el('span', 'version-dot'));
    btn.append(top, el('div', 'version-sub', v.subtitle));
    btn.addEventListener('click', () => {
      if (state.phase !== 'idle') return;
      state.selected = v.id;
      astra.saveSettings({ lastVersion: v.id });
      renderVersions();
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
    $('account-action').textContent = 'Sign out';
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
    state.account = res.value;
    setStatus(`Signed in as ${res.value.name}`);
    loadAstra();
  } else if (res.cancelled) {
    setStatus('Ready');
  } else {
    setStatus(res.error, true);
    toast(res.error);
  }
  renderAccount();
}

$('account-action').addEventListener('click', async () => {
  if (state.phase !== 'idle') return;
  if (state.account) {
    await astra.logout();
    state.account = null;
    state.astra = null;
    state.astraError = null;
    renderAstraAccount();
    setStatus('Signed out');
    renderAccount();
  } else {
    signIn();
  }
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
  const res = await astra.server.openWebsite();
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
$('nav-admin').addEventListener('click', openWebsite);

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
  });

  const java = $('java-path');
  java.value = settings.javaPath;
  java.addEventListener('change', () => astra.saveSettings({ javaPath: java.value }));

  $('open-folder').addEventListener('click', () => astra.openFolder(state.selected));
}

// ---------------------------------------------------------------- start

(async function init() {
  const [info, settings, account] = await Promise.all([astra.info(), astra.settings(), astra.account()]);

  if (info.ok) {
    state.versions = info.value.versions;
    $('app-version').textContent = `Astra Launcher ${info.value.version}`;
    $('game-folder').textContent = `${info.value.gameFolder}  (one folder per version)`;
    if (info.value.running) state.phase = 'running';
  }
  if (settings.ok) {
    if (state.versions.some((v) => v.id === settings.value.lastVersion)) {
      state.selected = settings.value.lastVersion;
    }
    bindSettings(settings.value);
  }
  if (account.ok) {
    state.account = account.value;
  }

  renderVersions();
  renderAccount();
  loadAstra();
})();
