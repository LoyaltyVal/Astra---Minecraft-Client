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
  cosmetics: null,
  confirmRemove: null
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
    loadCosmetics();
  }
}

document.querySelectorAll('.nav').forEach((btn) => {
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

// ---------------------------------------------------------------- cosmetics

const HINTS = {
  cape: 'Any picture works: Astra crops it to the cape. A standard 64×32 cape template (or HD 128×64, 256×128…) is used as-is.',
  wings: 'Use one wing with its root on the left and the tip on the right, or a pair side by side. A plain background is removed automatically.'
};

async function loadCosmetics() {
  const res = await astra.cosmetics.list();
  if (!res.ok) {
    toast(res.error);
    return;
  }
  state.cosmetics = res.value;
  renderCosmetics();
}

function renderCosmetics() {
  const type = state.cosmeticType;
  document.querySelectorAll('.seg').forEach((b) => {
    const active = b.dataset.type === type;
    b.classList.toggle('active', active);
    b.setAttribute('aria-selected', String(active));
  });
  $('cos-hint').textContent = HINTS[type];

  const grid = $('cos-grid');
  grid.innerHTML = '';
  const items = (state.cosmetics && state.cosmetics[type]) || [];

  if (!items.length) {
    const empty = el('div', 'cos-empty');
    empty.append(
      el('b', '', type === 'cape' ? 'No capes yet.' : 'No wings yet.'),
      document.createElement('br'),
      document.createTextNode('Click "Add images" to import PNG or JPG files.')
    );
    grid.appendChild(empty);
    return;
  }

  for (const item of items) {
    const card = el('button', 'cos' + (item.equipped ? ' equipped' : ''));
    card.title = item.equipped ? 'Click to unequip' : 'Click to equip';

    const thumb = el('div', 'cos-thumb' + (type === 'cape' ? ' cape' : ''));
    if (item.thumbnail) {
      const img = document.createElement('img');
      img.src = item.thumbnail;
      img.alt = '';
      thumb.appendChild(img);
    } else {
      thumb.appendChild(el('span', 'cos-state', "Couldn't read image"));
    }

    const remove = el('span', 'cos-remove');
    remove.setAttribute('role', 'button');
    remove.setAttribute('tabindex', '0');
    remove.title = state.confirmRemove === item.name ? 'Click again to move to the Recycle Bin' : 'Remove';
    remove.innerHTML = '<svg viewBox="0 0 16 16"><path d="M3 4.5h10M6.5 4.5V3h3v1.5M5 4.5l.6 8.5h4.8l.6-8.5"/></svg>';
    if (state.confirmRemove === item.name) {
      remove.style.opacity = '1';
      remove.style.background = 'var(--danger)';
    }
    remove.addEventListener('click', (e) => {
      e.stopPropagation();
      removeCosmetic(item.name);
    });

    card.append(
      thumb,
      remove,
      el('span', 'cos-name', item.label),
      el('span', 'cos-state', item.equipped ? 'Equipped' : 'Click to equip')
    );
    card.addEventListener('click', () => equipCosmetic(item.equipped ? null : item.name));
    grid.appendChild(card);
  }
}

async function equipCosmetic(name) {
  const res = await astra.cosmetics.equip(state.cosmeticType, name);
  if (!res.ok) {
    toast(res.error);
  }
  state.confirmRemove = null;
  loadCosmetics();
}

async function removeCosmetic(name) {
  if (state.confirmRemove !== name) {
    state.confirmRemove = name;
    renderCosmetics();
    return;
  }
  state.confirmRemove = null;
  const res = await astra.cosmetics.remove(state.cosmeticType, name);
  if (!res.ok) toast(res.error);
  else toast('Moved to the Recycle Bin', true);
  loadCosmetics();
}

document.querySelectorAll('.seg').forEach((b) => {
  b.addEventListener('click', () => {
    state.cosmeticType = b.dataset.type;
    state.confirmRemove = null;
    renderCosmetics();
  });
});

$('cos-import').addEventListener('click', async () => {
  const res = await astra.cosmetics.import(state.cosmeticType);
  if (!res.ok) {
    toast(res.error);
  } else if (res.value.length) {
    toast(`Added ${res.value.length} image${res.value.length > 1 ? 's' : ''}`, true);
  }
  loadCosmetics();
});
$('cos-folder').addEventListener('click', () => astra.cosmetics.openFolder(state.cosmeticType));

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
})();
