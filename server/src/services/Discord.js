'use strict';

const { RANKS } = require('./Ranks');

/**
 * Posts to a Discord channel through a webhook (config "discordWebhook"): new players and every
 * staff action from the activity log. Does nothing when no webhook is set. Messages are queued and
 * sent one at a time so Discord's rate limits are respected.
 */
class DiscordNotifier {
  constructor(config, fetchImpl) {
    this.url = /^https:\/\/(discord|discordapp)\.com\/api\/webhooks\//.test(config.discordWebhook || '') ? config.discordWebhook : null;
    this.publicUrl = config.publicUrl;
    this.fetch = fetchImpl;
    this.queue = [];
    this.sending = false;
  }

  get enabled() {
    return Boolean(this.url);
  }

  /** A line from the activity log, e.g. "NameTagged changed rank Steve (Player → Moderator)". */
  audit(entry) {
    if (!this.enabled || entry.action === 'signed in') return;
    const who = entry.actor ? entry.actor.name : 'System';
    const text = `**${escape(who)}** ${entry.action}${entry.target ? ` **${escape(entry.target)}**` : ''}${entry.detail ? ` · ${escape(entry.detail)}` : ''}`;
    this.send({ embeds: [{ description: text, color: 0x9b7bff, timestamp: entry.at }] });
  }

  newPlayer(player) {
    if (!this.enabled) return;
    this.send({
      embeds: [{
        title: `${player.name} joined Astra`,
        url: `${this.publicUrl}/player/${encodeURIComponent(player.name)}`,
        color: parseInt(RANKS.media.color.slice(1), 16),
        thumbnail: { url: `https://mc-heads.net/avatar/${player.uuid}/64` },
        timestamp: new Date().toISOString()
      }]
    });
  }

  send(body) {
    this.queue.push(body);
    if (this.queue.length > 50) this.queue.shift();
    this.drain();
  }

  async drain() {
    if (this.sending) return;
    this.sending = true;
    while (this.queue.length) {
      const body = this.queue.shift();
      try {
        const res = await this.fetch(this.url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: 'Astra', allowed_mentions: { parse: [] }, ...body })
        });
        if (res.status === 429) {
          const wait = Number(res.headers && res.headers.get && res.headers.get('retry-after')) || 2;
          this.queue.unshift(body);
          await new Promise((r) => setTimeout(r, Math.min(30, wait) * 1000));
        }
      } catch (e) {
        console.warn('[astra] Discord webhook failed:', e.message);
      }
      await new Promise((r) => setTimeout(r, 400));
    }
    this.sending = false;
  }
}

function escape(text) {
  return String(text).replace(/([*_`~|>\\])/g, '\\$1').replace(/@/g, '@\u200b');
}

module.exports = { DiscordNotifier };
