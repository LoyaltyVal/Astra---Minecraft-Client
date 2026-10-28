import { api } from '../api.js';
import { SiteChrome, Toast, avatar, el, rankBadge, timeAgo } from '../ui.js';

/**
 * The admin panel. Four tabs, each its own class:
 *   PlayersPanel    search players, change rank, ban, give and take cosmetics, add by name
 *   CosmeticsPanel  upload new capes and wings, edit rarity / free / hidden, delete
 *   UploadsPanel    approve or reject capes and wings that players uploaded
 *   ActivityPanel   the log of every staff action
 * What each tab lets you do depends on your rank; the server checks every action again.
 */

async function run(button, fn, done) {
  if (button) button.disabled = true;
  try {
    const out = await fn();
    if (done) Toast.show(done);
    return out;
  } catch (e) {
    Toast.show(e.message, { error: true });
    return null;
  } finally {
    if (button) button.disabled = false;
  }
}

class PlayersPanel {
  constructor(admin) {
    this.admin = admin;
    this.selected = null;
    this.list = [];
    this.timer = null;
  }

  render() {
    this.search = el('input', { type: 'search', placeholder: 'Search by name or UUID', 'aria-label': 'Search players',
      oninput: () => { clearTimeout(this.timer); this.timer = setTimeout(() => this.load(), 200); } });
    this.listEl = el('div', { class: 'plist', role: 'list' });
    this.detailEl = el('div', { class: 'pdetail card' }, el('p', { class: 'muted' }, 'Choose a player to manage them.'));
    const tools = [this.search];
    if (this.admin.can['cosmetics.grant']) {
      const name = el('input', { placeholder: 'Minecraft name', 'aria-label': 'Add player by name', maxlength: 16 });
      const add = el('button', { class: 'btn btn-quiet btn-sm', type: 'submit' }, 'Add player');
      tools.push(el('form', { class: 'addp', onsubmit: async (e) => {
        e.preventDefault();
        const r = await run(add, () => api.addPlayer(name.value.trim()), null);
        if (!r) return;
        Toast.show(`${r.player.name} added. Give them a rank or cosmetics before they sign in.`);
        name.value = '';
        await this.load();
        this.select(r.player);
      } }, name, add));
    }
    this.root = el('div', { class: 'players' },
      el('div', { class: 'pside' }, el('div', { class: 'ptools' }, tools), this.listEl),
      this.detailEl);
    this.load();
    return this.root;
  }

  async load() {
    try {
      const { players, total } = await api.players(this.search.value.trim());
      this.list = players;
      this.listEl.replaceChildren(
        el('p', { class: 'muted small' }, `${players.length} of ${total} players`),
        ...players.map((p) => el('button', {
          class: 'prow', role: 'listitem', 'aria-current': this.selected && this.selected.uuid === p.uuid ? 'true' : null,
          onclick: () => this.select(p)
        },
        el('img', { src: avatar(p.uuid, 48), alt: '' }),
        el('span', { class: 'pname' }, p.name, p.joinedAt ? null : el('small', { class: 'muted' }, ' not signed in yet')),
        p.banned ? el('span', { class: 'rank', style: { color: 'var(--rose)' } }, 'Banned') : rankBadge(p.rank),
        el('span', { class: `dot${p.online ? ' on' : ''}` }))));
      if (this.selected) {
        const fresh = players.find((p) => p.uuid === this.selected.uuid);
        if (fresh) this.select(fresh);
      }
    } catch (e) {
      this.listEl.replaceChildren(el('p', { class: 'muted' }, e.message));
    }
  }

  select(p) {
    this.selected = p;
    this.listEl.querySelectorAll('.prow').forEach((r, i) => {
      r.setAttribute('aria-current', String(this.list[i] && this.list[i].uuid === p.uuid));
    });
    const { can, me, ranks } = this.admin;
    const myLevel = ranks.find((r) => r.id === me.rank.id).level;
    const isMe = p.uuid === me.uuid;

    const head = el('div', { class: 'phead' },
      el('img', { src: avatar(p.uuid, 96), alt: '' }),
      el('div', {},
        el('h2', {}, p.name),
        el('p', { class: 'muted small' },
          p.joinedAt ? `Joined ${timeAgo(p.joinedAt)} · last seen ${timeAgo(p.lastSeen)}` : 'Added by staff, hasn\'t signed in yet'),
        p.playing ? el('p', { class: 'small', style: { color: 'var(--mint)' } },
          p.playing.server ? `In game on ${p.playing.server} (${p.playing.version})` : `In game (${p.playing.version}), server hidden`) : null,
        el('p', { class: 'mono small muted' }, p.uuid)),
      p.joinedAt ? el('a', { class: 'btn btn-quiet btn-sm', href: `/player/${encodeURIComponent(p.name)}`, target: '_blank' }, 'Profile') : null);

    const sections = [head];

    // Rank
    const rankSel = el('select', { 'aria-label': 'Rank', disabled: !can['ranks.set'] || isMe },
      ranks.map((r) => el('option', {
        value: r.id, selected: r.id === p.rank.id,
        disabled: me.rank.id !== 'owner' && r.level >= myLevel
      }, r.name)));
    const saveRank = el('button', { class: 'btn btn-quiet btn-sm', disabled: !can['ranks.set'] || isMe, onclick: async (e) => {
      const r = await run(e.currentTarget, () => api.setRank(p.uuid, rankSel.value), null);
      if (r) { Toast.show(`${p.name} is now ${r.player.rank.name}.`); this.load(); }
    } }, 'Save rank');
    sections.push(el('section', { class: 'psec' }, el('h3', {}, 'Rank'),
      el('div', { class: 'row' }, rankSel, saveRank),
      !can['ranks.set'] ? el('p', { class: 'muted small' }, 'Admins and Owners can change ranks.')
        : isMe ? el('p', { class: 'muted small' }, 'Another Owner or Admin has to change your rank.') : null));

    // Cosmetics
    const owned = new Set(p.ownedIds);
    const cosRows = this.admin.cosmetics.filter((c) => !c.free).map((c) => {
      const has = owned.has(c.id);
      return el('label', { class: 'cosrow' },
        el('input', { type: 'checkbox', checked: has, disabled: !can['cosmetics.grant'], onchange: async (e) => {
          const box = e.currentTarget;
          const r = await run(box, () => (box.checked ? api.grant(p.uuid, c.id) : api.revoke(p.uuid, c.id)),
            box.checked ? `Gave ${c.name} to ${p.name}.` : `Took ${c.name} from ${p.name}.`);
          if (!r) box.checked = !box.checked;
          else { this.selected = r.player; this.admin.refreshCosmetics(); }
        } }),
        el('img', { src: c.image, alt: '' }),
        el('span', {}, c.name, el('small', { class: `rarity rarity-${c.rarity}` }, ` ${c.rarity} ${c.type}`)),
        p.equippedIds[c.type] === c.id ? el('small', { class: 'eqtag' }, 'Wearing') : null);
    });
    sections.push(el('section', { class: 'psec' }, el('h3', {}, 'Cosmetics'),
      el('p', { class: 'muted small' }, 'Free cosmetics belong to everyone. Tick the special ones this player should have.'),
      cosRows.length ? el('div', { class: 'coslist' }, cosRows) : el('p', { class: 'muted small' }, 'There are no special cosmetics yet. Upload one in the Cosmetics tab and untick "Free".')));

    // Ban
    if (can['players.ban'] && !isMe) {
      const btn = el('button', { class: p.banned ? 'btn btn-quiet btn-sm' : 'btn btn-danger btn-sm', onclick: async (e) => {
        if (!p.banned && !confirm(`Ban ${p.name} from Astra? They'll be signed out and their cosmetics hidden.`)) return;
        const r = await run(e.currentTarget, () => api.setBanned(p.uuid, !p.banned), p.banned ? `${p.name} unbanned.` : `${p.name} banned.`);
        if (r) this.load();
      } }, p.banned ? 'Unban' : 'Ban from Astra');
      sections.push(el('section', { class: 'psec' }, el('h3', {}, 'Ban'),
        el('p', { class: 'muted small' }, p.banned ? 'This player is banned. They can\'t sign in and nobody sees their cosmetics.' : 'Banned players can\'t sign in to Astra and nobody sees their cosmetics.'),
        btn));
    }
    this.detailEl.replaceChildren(...sections);
  }
}

class CosmeticsPanel {
  constructor(admin) {
    this.admin = admin;
  }

  render() {
    this.grid = el('div', { class: 'cos-grid' });
    this.root = el('div', { class: 'cpanel' }, this.admin.can['cosmetics.manage'] ? this.uploadForm() : el('p', { class: 'muted' }, 'Admins and Owners can upload and edit cosmetics.'), this.grid);
    this.draw();
    return this.root;
  }

  uploadForm() {
    const file = el('input', { type: 'file', accept: 'image/png,image/jpeg', required: true });
    const preview = el('div', { class: 'upreview' }, el('span', { class: 'muted small' }, 'Preview'));
    const name = el('input', { required: true, maxlength: 40, placeholder: 'Galaxy' });
    const type = el('select', {}, el('option', { value: 'cape' }, 'Cape'), el('option', { value: 'wings' }, 'Wings (one wing image)'));
    const rarity = el('select', {}, this.admin.rarities.map((r) => el('option', { value: r }, r[0].toUpperCase() + r.slice(1))));
    const free = el('input', { type: 'checkbox' });
    const submit = el('button', { class: 'btn btn-primary', type: 'submit' }, 'Upload');
    let dataUrl = '';
    file.addEventListener('change', () => {
      const f = file.files[0];
      if (!f) return;
      if (f.size > 4 * 1024 * 1024) { Toast.show('Images must be 4 MB or smaller.', { error: true }); file.value = ''; return; }
      if (!name.value) name.value = f.name.replace(/\.[^.]+$/, '').replace(/[-_]+/g, ' ').slice(0, 40);
      const reader = new FileReader();
      reader.onload = () => {
        dataUrl = reader.result;
        preview.replaceChildren(el('img', { src: dataUrl, alt: 'Preview' }));
      };
      reader.readAsDataURL(f);
    });
    return el('form', { class: 'upload card', onsubmit: async (e) => {
      e.preventDefault();
      if (!dataUrl) { Toast.show('Choose an image first.', { error: true }); return; }
      const r = await run(submit, () => api.createCosmetic({ name: name.value, type: type.value, rarity: rarity.value, free: free.checked, image: dataUrl }),
        `${name.value} uploaded.${free.checked ? ' Everyone can wear it now.' : ' Give it to players from the Players tab.'}`);
      if (!r) return;
      e.target.reset();
      dataUrl = '';
      preview.replaceChildren(el('span', { class: 'muted small' }, 'Preview'));
      await this.admin.refreshCosmetics();
      this.draw();
    } },
    el('div', { class: 'up-head' }, el('h2', {}, 'Upload a cosmetic'),
      el('p', { class: 'muted small' }, 'Capes: any picture, it\'s fitted to the cape. Wings: a single wing on a plain background; Astra mirrors it into a pair and removes the background.')),
    el('div', { class: 'up-body' }, preview,
      el('div', { class: 'up-fields' },
        el('label', { class: 'field' }, 'Image (PNG or JPEG, up to 4 MB)', file),
        el('label', { class: 'field' }, 'Name', name),
        el('div', { class: 'row' }, el('label', { class: 'field' }, 'Type', type), el('label', { class: 'field' }, 'Rarity', rarity)),
        el('label', { class: 'check' }, free, 'Free for every player'),
        submit)));
  }

  draw() {
    const items = this.admin.cosmetics;
    if (!items.length) {
      this.grid.replaceChildren(el('div', { class: 'empty', style: { gridColumn: '1 / -1' } }, 'No cosmetics yet.'));
      return;
    }
    const manage = this.admin.can['cosmetics.manage'];
    this.grid.replaceChildren(...items.map((c) => {
      const tile = el('article', { class: `cos ${c.type} ${c.rarity}${c.hidden ? ' is-hidden' : ''}` },
        el('div', { class: 'art' }, el('img', { src: c.image, alt: '', loading: 'lazy' })),
        el('div', { class: 'cos-meta' }, el('b', {}, c.name), el('span', { class: `rarity rarity-${c.rarity}` }, c.rarity)),
        el('small', {}, `${c.type === 'cape' ? 'Cape' : 'Wings'} · ${c.free ? 'free for everyone' : `${c.owners} owner${c.owners === 1 ? '' : 's'}`}${c.hidden ? ' · hidden' : ''}`));
      if (manage) tile.append(this.editor(c));
      return tile;
    }));
  }

  editor(c) {
    const patch = async (box, body, msg) => {
      const r = await run(box, () => api.updateCosmetic(c.id, body), msg);
      if (r) { await this.admin.refreshCosmetics(); this.draw(); }
    };
    const rarity = el('select', { 'aria-label': 'Rarity', onchange: (e) => patch(e.currentTarget, { rarity: e.currentTarget.value }, 'Rarity saved.') },
      this.admin.rarities.map((r) => el('option', { value: r, selected: r === c.rarity }, r)));
    return el('details', { class: 'cedit' }, el('summary', {}, 'Edit'),
      el('div', { class: 'cedit-body' },
        el('label', { class: 'field' }, 'Rarity', rarity),
        el('label', { class: 'check' }, el('input', { type: 'checkbox', checked: c.free,
          onchange: (e) => patch(e.currentTarget, { free: e.currentTarget.checked }, e.currentTarget.checked ? `${c.name} is free for everyone.` : `${c.name} is now special.`) }), 'Free for everyone'),
        el('label', { class: 'check' }, el('input', { type: 'checkbox', checked: c.hidden,
          onchange: (e) => patch(e.currentTarget, { hidden: e.currentTarget.checked }, e.currentTarget.checked ? 'Hidden from the catalog.' : 'Shown in the catalog.') }), 'Hide from the catalog'),
        el('button', { class: 'btn btn-danger btn-sm', onclick: async (e) => {
          if (!confirm(`Delete ${c.name}? Anyone wearing it will have it taken off.`)) return;
          const r = await run(e.currentTarget, () => api.deleteCosmetic(c.id), `${c.name} deleted.`);
          if (r) { await this.admin.refreshCosmetics(); this.draw(); }
        } }, 'Delete')));
  }
}

class UploadsPanel {
  constructor(admin) {
    this.admin = admin;
  }

  render() {
    this.root = el('div', { class: 'cpanel' }, el('p', { class: 'muted' }, 'Loading…'));
    this.load();
    return this.root;
  }

  async load() {
    let uploads;
    try {
      ({ uploads } = await api.get('/api/admin/uploads'));
    } catch (e) {
      this.root.replaceChildren(el('p', { class: 'muted' }, e.message));
      return;
    }
    this.admin.setPending(uploads.length);
    if (!uploads.length) {
      this.root.replaceChildren(el('div', { class: 'card' }, el('p', { class: 'muted' },
        'Nothing to review. When players upload a cape or wings (Account page on the website), it waits here. Approved uploads belong only to the player who made them.')));
      return;
    }
    this.root.replaceChildren(
      el('p', { class: 'muted' }, `${uploads.length} waiting. Approve to let the player wear it (only they can). Reject deletes the image.`),
      el('div', { class: 'cos-grid' }, uploads.map((u) => {
        const card = el('article', { class: `cos ${u.type} common review` },
          el('div', { class: 'art' }, el('img', { src: u.image, alt: `${u.name} ${u.type}` })),
          el('div', { class: 'cos-meta' }, el('b', {}, u.name), el('span', { class: 'rarity' }, u.type === 'cape' ? 'Cape' : 'Wings')),
          u.uploader ? el('a', { class: 'small', href: `/player/${encodeURIComponent(u.uploader.name)}` },
            el('img', { src: avatar(u.uploader.uuid, 32), alt: '', class: 'tiny-head' }), ` ${u.uploader.name} · ${timeAgo(u.createdAt)}`) : null,
          el('div', { class: 'review-actions' },
            el('button', { class: 'btn btn-primary btn-sm', onclick: async (e) => {
              const r = await run(e.currentTarget, () => api.post(`/api/admin/uploads/${u.id}/approve`), `Approved. ${u.uploader ? u.uploader.name : 'The player'} can wear it now.`);
              if (r) this.load();
            } }, 'Approve'),
            el('button', { class: 'btn btn-danger btn-sm', onclick: async (e) => {
              const reason = prompt(`Why are you rejecting "${u.name}"? The player sees this. (Optional)`, '');
              if (reason === null) return;
              const r = await run(e.currentTarget, () => api.post(`/api/admin/uploads/${u.id}/reject`, { reason }), 'Rejected and deleted.');
              if (r) this.load();
            } }, 'Reject')));
        return card;
      })));
  }
}

class ActivityPanel {
  render() {
    this.root = el('div', { class: 'activity card' }, el('p', { class: 'muted' }, 'Loading…'));
    this.load();
    return this.root;
  }

  async load() {
    try {
      const { entries } = await api.audit();
      if (!entries.length) {
        this.root.replaceChildren(el('p', { class: 'muted' }, 'Nothing has happened yet. Every rank change, ban, gift and upload shows up here.'));
        return;
      }
      this.root.replaceChildren(el('ol', { class: 'log' }, entries.map((e) => el('li', {},
        e.actor ? el('img', { src: avatar(e.actor.uuid, 32), alt: '' }) : el('span', { class: 'sysdot' }),
        el('p', {},
          el('b', {}, e.actor ? e.actor.name : 'System'), ` ${e.action}`,
          e.target ? [' ', el('b', {}, e.target)] : null,
          e.detail ? el('span', { class: 'muted' }, ` · ${e.detail}`) : null),
        el('time', { datetime: e.at, title: new Date(e.at).toLocaleString() }, timeAgo(e.at))))));
    } catch (err) {
      this.root.replaceChildren(el('p', { class: 'muted' }, err.message));
    }
  }
}

class AdminPage {
  async init() {
    await new SiteChrome(null).mount();
    this.root = document.querySelector('[data-admin]');
    let who;
    try {
      who = await api.whoami();
    } catch (e) {
      this.root.replaceChildren(e.status === 401 ? AdminPage.signIn() : AdminPage.denied());
      return;
    }
    this.me = who.player;
    this.can = who.can;
    const [meta, stats] = await Promise.all([api.meta(), api.stats()]);
    this.ranks = meta.ranks.slice().sort((a, b) => b.level - a.level);
    this.rarities = meta.rarities;
    await this.refreshCosmetics();

    const panels = { players: new PlayersPanel(this), cosmetics: new CosmeticsPanel(this), activity: new ActivityPanel() };
    if (this.can['uploads.review']) panels.uploads = new UploadsPanel(this);
    const body = el('div', { class: 'admin-body' });
    const tabs = el('div', { class: 'tabs', role: 'tablist', 'aria-label': 'Admin sections' });
    const open = (key) => {
      tabs.querySelectorAll('.tab').forEach((t) => t.setAttribute('aria-selected', String(t.dataset.key === key)));
      body.replaceChildren(panels[key].render());
      try { sessionStorage.setItem('astra.adminTab', key); } catch { /* private mode */ }
    };
    for (const [key, label] of [['players', 'Players'], ['cosmetics', 'Cosmetics'], ['uploads', 'Uploads'], ['activity', 'Activity']]) {
      if (!panels[key]) continue;
      const tab = el('button', { class: 'tab', role: 'tab', 'data-key': key, onclick: () => open(key) }, label);
      if (key === 'uploads') {
        this.pendingBadge = el('span', { class: 'tab-badge', hidden: true });
        tab.append(this.pendingBadge);
      }
      tabs.append(tab);
    }
    if (panels.uploads) {
      api.get('/api/admin/uploads').then(({ uploads }) => this.setPending(uploads.length)).catch(() => {});
    }
    this.root.replaceChildren(
      el('div', { class: 'admin-head' },
        el('div', {}, el('p', { class: 'kicker' }, 'Admin panel'), el('h1', {}, 'Run Astra')),
        el('div', { class: 'admin-me' }, el('img', { src: avatar(this.me.uuid, 56), alt: '' }),
          el('div', {}, el('b', {}, this.me.name), rankBadge(this.me.rank)),
          el('a', { class: 'btn btn-quiet btn-sm', href: '/logout' }, 'Sign out'))),
      el('div', { class: 'stats' },
        AdminPage.stat(stats.players, 'players'), AdminPage.stat(stats.online, 'online now'),
        AdminPage.stat(stats.cosmetics, 'cosmetics'), AdminPage.stat(stats.staff, 'team members')),
      tabs, body);
    let first = 'players';
    try { first = sessionStorage.getItem('astra.adminTab') || first; } catch { /* private mode */ }
    open(panels[first] ? first : 'players');
  }

  setPending(n) {
    if (!this.pendingBadge) return;
    this.pendingBadge.hidden = !n;
    this.pendingBadge.textContent = String(n);
  }

  async refreshCosmetics() {
    this.cosmetics = (await api.allCosmetics()).cosmetics;
  }

  static stat(n, label) {
    return el('div', { class: 'stat' }, el('b', {}, Number(n).toLocaleString()), el('span', {}, label));
  }

  static signIn() {
    return el('div', { class: 'gate card' },
      el('p', { class: 'kicker' }, 'Admin panel'),
      el('h1', {}, 'Sign in from the Astra app'),
      el('p', { class: 'muted' }, 'The website doesn\'t take passwords. Open the Astra app, sign in with Microsoft, then click Admin panel in the sidebar. It opens this page already signed in.'),
      el('a', { class: 'btn btn-quiet', href: '/' }, 'Back to home'));
  }

  static denied() {
    return el('div', { class: 'gate card' },
      el('p', { class: 'kicker' }, 'Admin panel'),
      el('h1', {}, 'Staff only'),
      el('p', { class: 'muted' }, 'You\'re signed in, but the admin panel is for Moderators and above. Ask an Owner if you think that\'s wrong.'),
      el('a', { class: 'btn btn-quiet', href: '/logout' }, 'Sign out'));
  }
}

new AdminPage().init();
