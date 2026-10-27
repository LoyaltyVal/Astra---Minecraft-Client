import { api } from '../api.js';
import { SiteChrome, el } from '../ui.js';

const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/** The hero's fake HUD: keystrokes light up and the counters wander. */
class HeroHud {
  constructor(root) {
    this.keys = {};
    root.querySelectorAll('.key').forEach((k) => { this.keys[k.dataset.k] = k; });
    this.fps = root.querySelector('#fps');
    this.cps = root.querySelector('#cps');
  }

  press(k, ms) {
    const key = this.keys[k];
    if (!key) return;
    key.classList.add('on');
    setTimeout(() => key.classList.remove('on'), ms);
  }

  start() {
    if (reduced) return;
    this.keys.w.classList.add('on');
    let t = 0;
    setInterval(() => {
      t++;
      if (Math.random() < 0.55) this.press('l', 45);
      if (t % 18 === 0) this.press(Math.random() < 0.5 ? 'a' : 'd', 380);
      if (t % 40 === 0) this.press('space', 160);
      if (t % 26 === 0) this.press('r', 120);
      if (t % 10 === 0) {
        this.fps.textContent = String(236 + Math.floor(Math.random() * 18));
        this.cps.textContent = String(10 + Math.floor(Math.random() * 4));
      }
    }, 80);
  }
}

/** A quiet twinkling starfield behind the page. */
class Starfield {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.stars = [];
  }

  resize() {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    this.canvas.width = innerWidth * dpr;
    this.canvas.height = innerHeight * dpr;
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const count = Math.round((innerWidth * innerHeight) / 12000);
    this.stars = Array.from({ length: count }, () => ({
      x: Math.random() * innerWidth, y: Math.random() * innerHeight, r: Math.random() * 1.1 + 0.2,
      p: Math.random() * Math.PI * 2, s: Math.random() * 0.8 + 0.2
    }));
    this.draw(0);
  }

  draw(time) {
    const { ctx } = this;
    ctx.clearRect(0, 0, innerWidth, innerHeight);
    for (const st of this.stars) {
      const a = reduced ? 0.45 : 0.2 + 0.4 * Math.abs(Math.sin(st.p + time * 0.0005 * st.s));
      ctx.fillStyle = `rgba(241,239,255,${a})`;
      ctx.beginPath();
      ctx.arc(st.x, st.y, st.r, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  start() {
    addEventListener('resize', () => this.resize());
    this.resize();
    if (reduced) return;
    const loop = (t) => { this.draw(t); requestAnimationFrame(loop); };
    requestAnimationFrame(loop);
  }
}

class HomePage {
  async init() {
    new HeroHud(document.querySelector('.game')).start();
    new Starfield(document.getElementById('sky')).start();
    await new SiteChrome('home').mount();
    await Promise.all([this.loadStats(), this.loadRack()]);
  }

  async loadStats() {
    try {
      const s = await api.stats();
      for (const [k, v] of Object.entries(s)) {
        const b = document.querySelector(`[data-stat="${k}"]`);
        if (b) b.textContent = Number(v).toLocaleString();
      }
    } catch {
      // Leave the placeholder text; the page works without live numbers.
    }
  }

  /** Shows four cosmetics: the newest capes and wings, preferring a mix of both. */
  async loadRack() {
    const rack = document.querySelector('[data-rack]');
    try {
      const { cosmetics } = await api.cosmetics();
      const capes = cosmetics.filter((c) => c.type === 'cape');
      const wings = cosmetics.filter((c) => c.type === 'wings');
      const pick = [...capes.slice(0, 2), ...wings.slice(0, 2)];
      for (const c of cosmetics) if (pick.length < 4 && !pick.includes(c)) pick.push(c);
      rack.replaceChildren(...pick.map((c) => el('a', { class: `item ${c.type}`, href: '/cosmetics', style: { textDecoration: 'none' } },
        el('div', { class: 'art' }, el('img', { src: c.image, alt: `${c.name} ${c.type}`, loading: 'lazy' })),
        el('b', {}, c.name),
        el('small', { class: `rarity-${c.rarity}` }, `${c.rarity} ${c.type === 'cape' ? 'cape' : 'wings'}`))));
    } catch {
      rack.replaceChildren(el('p', { class: 'muted' }, 'Cosmetics couldn\'t be loaded right now.'));
    }
  }
}

new HomePage().init();
