// Shared page chrome (nav, footer, toasts) and small DOM helpers.
import { api } from './api.js';

const STAR = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 1.5l2.6 7.9 7.9 2.6-7.9 2.6L12 22.5l-2.6-7.9L1.5 12l7.9-2.6z"/></svg>';
const DOWNLOAD = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3v12m0 0l-5-5m5 5l5-5M4 17v3h16v-3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const DISCORD = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20.3 4.4A19.6 19.6 0 0 0 15.4 3l-.6 1.3a18.2 18.2 0 0 0-5.6 0L8.6 3a19.5 19.5 0 0 0-4.9 1.5C.6 9.1-.3 13.6.1 18a19.8 19.8 0 0 0 6 3l1.3-2a12.8 12.8 0 0 1-2-1l.5-.4a14 14 0 0 0 12.2 0l.5.4a12.8 12.8 0 0 1-2 1l1.3 2a19.7 19.7 0 0 0 6-3c.5-5.1-.8-9.6-3.6-13.6zM8 15.3c-1.2 0-2.2-1.1-2.2-2.4s1-2.4 2.2-2.4 2.2 1.1 2.2 2.4-1 2.4-2.2 2.4zm8 0c-1.2 0-2.2-1.1-2.2-2.4s1-2.4 2.2-2.4 2.2 1.1 2.2 2.4-1 2.4-2.2 2.4z"/></svg>';
const WINDOWS = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 4.6l7.4-1v7.1H3zm8.3-1.1L21 2v8.7h-9.7zM3 11.6h7.4v7.1L3 17.7zm8.3 0H21V21l-9.7-1.4z"/></svg>';

/** Creates an element: el('a', { href: '/', class: 'x' }, 'text', child). */
export function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === null || v === undefined || v === false) continue;
    if (k === 'class') node.className = v;
    else if (k === 'style' && typeof v === 'object') Object.assign(node.style, v);
    else if (k.startsWith('on') && typeof v === 'function') node.addEventListener(k.slice(2), v);
    else node.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) {
    if (c === null || c === undefined || c === false) continue;
    node.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return node;
}

// If the skin service can't be reached, show a plain head instead of a broken image.
const FALLBACK_HEAD = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 8 8" shape-rendering="crispEdges"><rect width="8" height="8" fill="#3b2a1e"/><rect y="2" width="8" height="6" fill="#b98863"/><rect x="1" y="4" width="2" height="1" fill="#fff"/><rect x="2" y="4" width="1" height="1" fill="#4a3aa0"/><rect x="5" y="4" width="2" height="1" fill="#fff"/><rect x="5" y="4" width="1" height="1" fill="#4a3aa0"/><rect x="3" y="6" width="2" height="1" fill="#6b4430"/></svg>');
document.addEventListener('error', (e) => {
  const img = e.target;
  if (img.tagName === 'IMG' && img.src.startsWith('https://mc-heads.net/') && !img.dataset.fallback) {
    img.dataset.fallback = '1';
    img.src = FALLBACK_HEAD;
  }
}, true);

export const avatar = (uuid, size = 64) => `https://mc-heads.net/avatar/${uuid}/${size}`;
export const body = (uuid, size = 220) => `https://mc-heads.net/body/${uuid}/${size}`;

export function rankBadge(rank) {
  return el('span', { class: 'rank', style: { color: rank.color } }, rank.name);
}

export function timeAgo(iso) {
  if (!iso) return 'never';
  const s = Math.max(0, (Date.now() - Date.parse(iso)) / 1000);
  if (s < 60) return 'just now';
  if (s < 3600) return `${Math.floor(s / 60)} min ago`;
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
  if (s < 86400 * 30) return `${Math.floor(s / 86400)} days ago`;
  return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

/** A cosmetic tile used on the catalog, profile and admin pages. */
export function cosmeticTile(c, extra) {
  return el('article', { class: `cos ${c.type} ${c.rarity}` },
    el('div', { class: 'art' }, el('img', { src: c.image, alt: `${c.name} ${c.type === 'cape' ? 'cape' : 'wings'}`, loading: 'lazy' })),
    el('div', { class: 'cos-meta' }, el('b', {}, c.name), el('span', { class: `rarity rarity-${c.rarity}` }, c.rarity)),
    el('small', {}, `${c.type === 'cape' ? 'Cape' : 'Wings'}${c.free ? ' · Free for everyone' : ''}`),
    extra || null);
}

export class Toast {
  static show(message, { error = false } = {}) {
    let t = document.getElementById('toast');
    if (!t) {
      t = el('div', { id: 'toast', class: 'toast', role: 'status' });
      document.body.append(t);
    }
    t.textContent = message;
    t.classList.toggle('error', error);
    t.hidden = false;
    clearTimeout(Toast.timer);
    Toast.timer = setTimeout(() => { t.hidden = true; }, 4200);
  }
}

/**
 * Renders the shared nav and footer into <header data-nav> and <footer data-footer>, highlights the
 * current page, and shows the signed-in player (with an Admin link for staff).
 */
export class SiteChrome {
  constructor(active) {
    this.active = active;
  }

  async mount() {
    const header = document.querySelector('[data-nav]');
    const footer = document.querySelector('[data-footer]');
    const links = [['/players', 'Players', 'players'], ['/cosmetics', 'Cosmetics', 'cosmetics'], ['/team', 'Team', 'team'], ['/friends', 'Friends', 'friends'], ['/#changelog', 'Changelog', 'changelog']];
    if (header) {
      header.className = 'nav';
      header.innerHTML = `<div class="wrap"><a class="brand" href="/" aria-label="Astra home">${STAR}ASTRA</a>
        <nav class="nav-links" aria-label="Site"></nav><div class="nav-user" data-user></div></div>`;
      const nav = header.querySelector('.nav-links');
      for (const [href, label, key] of links) {
        nav.append(el('a', { href, 'aria-current': key === this.active ? 'page' : null }, label));
      }
    }
    if (footer) {
      footer.innerHTML = `<div class="wrap foot">
        <div class="foot-brand"><a class="brand" href="/" aria-label="Astra home">${STAR}ASTRA</a>
          <p>A free PvP client for Minecraft 1.8.9 and 1.7.10.</p></div>
        <nav aria-label="Client"><b>Client</b><a class="js-download" href="/#download">Download</a><a href="/#changelog">Changelog</a><a href="/#faq">FAQ</a></nav>
        <nav aria-label="Community" data-community><b>Community</b><a href="/players">Players</a><a href="/cosmetics">Cosmetics</a><a href="/team">Team</a></nav>
        <nav aria-label="Account" data-account><b>Account</b></nav>
      </div>
      <div class="wrap foot-legal"><p>© ${new Date().getFullYear()} Astra Client. Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.</p></div>`;
    }
    this.meta = await api.meta().catch(() => ({ downloadUrl: '' }));
    this.me = await api.me().catch(() => null);
    this.renderUser();
    this.wireDownloads();
    this.wireDiscord();
    return this;
  }

  /** Footer account links depend on who's signed in; the admin panel link is for staff only. */
  renderAccountLinks() {
    const nav = document.querySelector('[data-account]');
    if (!nav) return;
    nav.querySelectorAll('a').forEach((a) => a.remove());
    if (this.me) {
      nav.append(el('a', { href: `/player/${encodeURIComponent(this.me.name)}` }, 'My profile'));
      nav.append(el('a', { href: '/friends' }, 'Friends'));
      nav.append(el('a', { href: '/account' }, 'Settings'));
      if (this.me.permissions.includes('admin.view')) nav.append(el('a', { href: '/admin' }, 'Admin panel'));
      nav.append(el('a', { href: '/logout' }, 'Sign out'));
    } else {
      nav.append(el('a', { href: '/account' }, 'Sign in'));
    }
  }

  renderUser() {
    this.renderAccountLinks();
    const slot = document.querySelector('[data-user]');
    if (!slot) return;
    slot.innerHTML = '';
    if (this.me) {
      if (this.me.permissions.includes('admin.view')) slot.append(el('a', { class: 'btn btn-quiet btn-sm', href: '/admin' }, 'Admin'));
      slot.append(el('a', { href: `/player/${encodeURIComponent(this.me.name)}`, class: 'nav-user' },
        el('img', { src: avatar(this.me.uuid, 56), alt: '' }), this.me.name));
    }
    const a = el('a', { class: 'btn btn-quiet btn-sm js-download nav-dl', href: '/#download' });
    a.innerHTML = `${DOWNLOAD}Download`;
    slot.append(a);
  }

  /** Adds Discord links (nav and footer) when the server has an invite set. */
  wireDiscord() {
    const invite = this.meta && this.meta.discordInvite;
    if (!invite) return;
    const slot = document.querySelector('[data-user]');
    if (slot) {
      const a = el('a', { class: 'btn btn-discord btn-sm', href: invite, target: '_blank', rel: 'noopener', title: 'Join the Astra Discord' });
      a.innerHTML = `${DISCORD}<span class="sr">Discord</span>`;
      slot.prepend(a);
    }
    const community = document.querySelector('[data-community]');
    if (community) community.append(el('a', { href: invite, target: '_blank', rel: 'noopener' }, 'Discord'));
    document.querySelectorAll('.js-discord').forEach((b) => { b.href = invite; b.hidden = false; });
  }

  /** Points every .js-download link at the configured installer, or explains it's coming soon. */
  wireDownloads() {
    const url = this.meta && this.meta.downloadUrl;
    const info = this.meta && this.meta.download;
    if (url && info) {
      // "Astra 1.0.0 · 92 MB · Windows 10 & 11, 64-bit"
      const parts = [];
      if (info.version) parts.push(`Astra ${info.version}`);
      if (info.size) parts.push(`${Math.max(1, Math.round(info.size / 1048576))} MB`);
      parts.push('Windows 10 & 11, 64-bit');
      document.querySelectorAll('.js-download-note').forEach((p) => { p.textContent = parts.join(' · '); });
    }
    document.querySelectorAll('.js-download').forEach((a) => {
      if (url) {
        a.href = url;
        a.rel = 'noopener';
        a.setAttribute('download', '');
        // Astra isn't code-signed yet, so Windows warns the first time it's run.
        a.addEventListener('click', () => {
          Toast.show('Downloading. If Windows says "Windows protected your PC", click More info, then Run anyway.');
        });
      } else {
        a.addEventListener('click', (e) => {
          e.preventDefault();
          Toast.show('The Windows download is almost ready. Check back soon.');
        });
      }
    });
  }
}

export { STAR, WINDOWS };
