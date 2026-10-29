<div align="center">

# ✦ Astra Client

**The Minecraft client built for PvP.**
One app for Minecraft 1.8.9 and 1.7.10. Sign in, press Launch, and your HUD, zoom, toggle sprint and cosmetics are already there, with capes, wings and rank tags that every Astra player can see.

![Minecraft 1.8.9](https://img.shields.io/badge/Minecraft-1.8.9-9b7bff?style=flat-square)
![Minecraft 1.7.10](https://img.shields.io/badge/Minecraft-1.7.10-9b7bff?style=flat-square)
![Java 8](https://img.shields.io/badge/Java-8-6c4ee8?style=flat-square)
![Legacy Fabric](https://img.shields.io/badge/Legacy%20Fabric-0.18.3-6c4ee8?style=flat-square)
![Electron](https://img.shields.io/badge/launcher-Electron-3d3a5c?style=flat-square)
![Platform](https://img.shields.io/badge/platform-Windows-3d3a5c?style=flat-square)

<img src="docs/images/hero.png" alt="The Astra website: the Astra app in front of Minecraft with Astra's HUD" width="820">

</div>

---

## Contents

- [Features](#features)
- [Screenshots](#screenshots)
- [For players](#for-players)
- [Cosmetics](#cosmetics)
- [Run an Astra server](#run-an-astra-server)
- [Players and Discord](#players-and-discord)
- [For developers](#for-developers)
- [Project structure](#project-structure)
- [Roadmap](#roadmap)
- [License](#license)

## Features

**One app.** The Astra Launcher handles Microsoft sign-in and downloads Java 8, Minecraft and the mod loader for you. Your settings, resource packs and server list are shared with your normal `.minecraft`, and Astra's mods stay in their own folder.

**Everything in one menu.** Press **Right Shift** in game. Every feature is a card with a live preview: click to turn it on, open **Options** to tune colors, size, backgrounds and keybinds.

**A HUD you arrange.** The HUD editor lets you drag anything anywhere. It snaps to the screen edges and center, scroll resizes, and layouts are saved per version.

**Capes and wings from your own art.** Any picture becomes a cape, and a single wing becomes a flapping pair.

**Radio.** Listen to internet radio while you play: Right Shift → MODS → **Radio** → **OPEN**. Pick a station, set the volume, or paste any MP3 stream (or a `.pls` / `.m3u` link). The song title shows on the HUD, music keeps playing when you close the menu, and it can pick up where you left off next launch. Stations come from your Astra server (`radioStations` in `server/config.json`); the built-in list is SomaFM and Radio Paradise.

**Shortcuts.** Friends is part of the client, not a mod: it has its own button in the top-right corner of the HUD editor (Right Shift), with how many friends are online and a red badge for messages and requests, as well as **Shift + Tab**. **Quick Join** is the Friends menu's **Servers** tab: favourite servers (number keys 1-9), recently played ones, and how many friends are on each. Waypoints, Screenshots and Radio are mods with an **OPEN** button on their cards, and the title screen has Quick Join, Friends and Screenshots. Favourite servers, waypoints and starred screenshots are kept in `%APPDATA%\.astra\data`, so 1.8.9 and 1.7.10 share them.

**Friends and emotes.** Press **Shift + Tab** in game for your friends: see what server they're on, message them and join them in one click. Hold **B** for the emote wheel; every Astra player sees your emote. The same friends show in the app and on the website.

### Built-in features

| Feature | What it does | Default |
|---|---|:---:|
| FPS / CPS | Frames and clicks per second | On |
| Keystrokes | WASD, mouse buttons with CPS and space, with press fades | On |
| Scoreboard | Movable sidebar; hide the red numbers, change the opacity | On |
| Armor Status | Armor and held item with durability (value or percent) | On |
| Potion Effects | Active effects with icons and time left; blinks when ending | On |
| Boss Bar | Move or resize the wither and dragon bar | On |
| Toggle Sprint | Tap Left Ctrl to keep sprinting | On |
| Zoom | Hold **C**; smooth, lowers mouse sensitivity while zoomed | On |
| Freelook | Hold **Left Alt** (or toggle) to look around your player in third person while you keep facing the same way | On |
| Direction HUD | Compass strip with your heading in degrees | Off |
| Coordinates | X, Y, Z and facing | Off |
| Ping / Speed | Latency to the server; blocks per second | Off |
| Clock / Memory | Local time; RAM use | Off |
| Fullbright | Full brightness; restores your gamma when turned off | Off |
| Pot Counter | Healing potions left in your inventory | Off |
| 1.7 Animations | Block-hitting, 1.7 eating, drinking and bow, red armor when hit, smooth sneaking (1.8.9) | On |
| No Hurtcam | Turns off the screen shake when you take damage | Off |
| No Enchant Glint | Hides the glint on items, armor or both | Off |
| Chat | Keeps up to 5000 lines of chat to scroll back through; stacks repeated messages as "(x3)" | On |
| Auto Text | Up to 6 hotkeys that send a message or command (e.g. F6 = `/play duels_classic_duel`) | On |
| Emotes | Hold **B** for a wheel of 10 emotes that every Astra player sees | On |
| Auto GG | Says "gg" (or your own message, plus an optional second line) when a game ends. Spots end-of-game lines on Hypixel and most minigame servers, never what players type; add your own triggers | Off |
| Waypoints | **M** opens your waypoints, **N** drops one where you stand. Markers show on screen with distance and point to ones off screen; dying leaves a Death waypoint. Saved per server and world | On |
| Radio | Internet radio while you play (OPEN on its card): stations, volume, your own stream links. The current song shows on your HUD | On |
| Screenshot Manager | Browse screenshots as thumbnails, star favourites, copy to the clipboard, open, show in folder, rename and delete | On |
| Performance | Lower FPS when the game is in the background, no fog, fewer particles, no entity shadows | On |
| Borderless Fullscreen | F11 goes borderless instead of exclusive fullscreen | On |
| Menu Background | Astra's background on the title screen, or your own picture from `%APPDATA%\.astra\backgrounds` | On |
| Client Settings | Accent color, no heart flashing, centered inventory, your own nametag in F5 | On |

Astra only includes visual and quality-of-life features. There's no reach, autoclicker or x-ray, and there never will be.

## Screenshots

| Launcher | Cosmetics catalog |
|---|---|
| <img src="docs/images/launcher.png" alt="Astra Launcher home: version picker, account and Launch button" width="420"> | <img src="docs/images/website-cosmetics.png" alt="The website's cosmetics catalog with Equip buttons" width="420"> |

**Admin panel:** players, ranks, gifts and bans, cosmetic uploads and an activity log.

<img src="docs/images/admin.png" alt="The Astra admin panel: player list with ranks, and a player's rank, cosmetics and ban controls" width="840">

## For players

**Requirements:** Windows 10 or 11 (64-bit) and a Microsoft account that owns Minecraft: Java Edition.

1. Download the installer from [Releases](../../releases) and run it.
2. Open **Astra Launcher** and sign in with Microsoft.
3. Pick **1.8.9** or **1.7.10** and press **Launch**. The first launch downloads the game, which takes a few minutes.
4. In game, press **Right Shift** to open the Astra menu (it opens on the HUD editor; the centre **Mods** button opens the menu). **Shift + Tab** opens Friends and **B** opens the emote wheel.

Several Microsoft accounts can be saved: click **Switch account** under your name on the Play tab, or manage them all in **Settings → Accounts**. Each account keeps its own Astra sign-in, so switching is instant. If Microsoft's sign-in for an account expires, it's marked **Sign in again**.

**Switch accounts in game** without restarting: click your name in the top-left of the title screen, or next to Friends in the HUD editor (Right Shift). Pick an account or add one (Microsoft's sign-in window opens from the Astra app, sometimes behind the game). Switching leaves the world or server you're on. Your accounts stay in the Astra app: the game asks it through a private connection on your own PC (only reachable from this computer, protected by a random key for each game session).

**Signing in.** The app keeps each account's Minecraft token for its whole lifetime (about a day), so it only signs in to Microsoft, Xbox and Minecraft when needed, once at a time. If Minecraft's sign-in is busy (it limits how often you can sign in), the app waits and tries again, and keeps using the token it has. An account is only marked **Sign in again** when Microsoft really rejects the saved sign-in. Resource packs can sit in sub-folders of `resourcepacks` (for example `resourcepacks\PvP\…`) and still show up in game. Your keybinds and settings come from your normal `.minecraft`.

| Where | What's there |
|---|---|
| `%APPDATA%\.astra\instances\<version>` | Worlds, options, screenshots and Astra settings (`config/astra.json`) for each version |
| `%APPDATA%\Astra Launcher\logs` | Launcher and game logs (also in the Logs tab) |

Resource packs, screenshots, the server list and your settings are shared with your normal `.minecraft` folder (turn this off in **Settings → Share with .minecraft**). If `.minecraft` holds settings from a newer Minecraft version, only settings both versions understand (FOV, brightness, render distance, volume, resource packs and so on) are shared, so the official game's keybinds are never touched.

Sign-in uses Microsoft's own page. Astra stores only a refresh token, encrypted with Windows' data protection (DPAPI), never your password.

## Cosmetics

Cosmetics live on the **Astra server**, so everyone sees them. When you launch, the app signs you in to the server and the game looks up every player around you, so any Astra player's cape, wings and rank tag (like **[Owner]** or **[Moderator]**) show for you, and yours show for them.

- **Wear something:** in the app's **Cosmetics** tab, in game with **Right Shift → Cosmetics**, or on the website's Cosmetics page. Whatever you pick shows for everyone within a few seconds.
- **Get more:** free cosmetics belong to everyone. Staff can give anyone special ones from the admin panel.
- **Options in game:** show or hide capes, wings, other players' cosmetics and rank tags; wing size and flap speed.

Players on other clients see the normal game. Cosmetics are matched by Minecraft account UUID, so they show on online-mode servers (Hypixel and the like), not on cracked/offline-mode servers.

### Making new cosmetics

Staff upload images in the admin panel's **Cosmetics** tab. The client turns them into textures:

- **Capes:** any picture works. Astra crops it to the cape's 10:16 shape and wraps it onto the cape. A standard 64×32 cape template (or HD 128×64, 256×128 and so on) is used as-is.
- **Wings:** one wing with its root on the **left** and its tip on the **right**, or a pair side by side. A plain background like white is removed automatically, but a transparent PNG gives the cleanest edges.

Prompts that work well in an AI image tool:

> **Cape:** *A vertical banner design, 10:16 portrait, [your theme], flat bold colors, no text, fills the whole image.*
>
> **Wing:** *A single [angel/dragon/feather] wing, side view, attached on the left and extending right, centered, on a plain white background, no shadow.*

Keep details big: a cape is only 10×16 Minecraft pixels on screen.

## Run an Astra server

The server in [`server/`](server) is the website, the API the client and app talk to, and a small JSON database, all in one Node.js app with no dependencies.

```bash
cd server
cp config.example.json config.json   # then put your Minecraft name in "owners"
npm start                            # http://localhost:8787
npm test                             # API tests
```

1. Start the server, then open the Astra app, go to **Settings → Astra server** and enter its address (the default is `http://localhost:8787`).
2. Launch once. You're signed in with your Minecraft account and, because your name is in `owners`, you're the **Owner**.
3. Click **Admin** in the app's sidebar. The admin panel opens in your browser, already signed in.

| Rank | Can |
|---|---|
| **Owner** | Everything, including making other Owners |
| **Admin** | Everything below, plus upload, edit and delete cosmetics and change ranks below Admin |
| **Developer** | Everything below, plus give and take cosmetics |
| **Moderator** | Open the admin panel, ban and unban players, approve or reject player uploads |
| **Media** | A tag in game and a spot on the Team page |
| **Player** | Wear free cosmetics, ones they're given, and their own approved uploads |

Every staff action is recorded in the admin panel's **Activity** tab. Ranks and permissions are defined in [`server/src/services/Ranks.js`](server/src/services/Ranks.js).

**How sign-in works:** the Astra server never sees a Microsoft password or token. It works like joining a Minecraft server: the server hands out a one-time challenge, the app tells Mojang's session server it's joining that challenge, and the Astra server asks Mojang to confirm. The website has no passwords at all; the app opens it with a one-time sign-in link.

**Config** (`config.json`, or environment variables):

| Key | Env | Meaning |
|---|---|---|
| `port` | `PORT` | Port to listen on (default 8787) |
| `publicUrl` | `ASTRA_PUBLIC_URL` | The address players use, e.g. `https://astra.example.com` |
| `owners` | `ASTRA_OWNERS` | Minecraft names that are always Owner (comma-separated in the env var) |
| `downloadUrl` | `ASTRA_DOWNLOAD_URL` | Where the website's Download buttons point (e.g. a GitHub Release) |
| `dataDir` | `ASTRA_DATA_DIR` | Where players, cosmetics and images are stored (default `data/`) |
| `devAuth` | `ASTRA_DEV_AUTH` | **Testing only:** skips the Mojang check, so anyone can claim any name. The server refuses to start with it on a public address |
| `radioStations` | | The in-game radio's stations: `[{ "id", "name", "genre", "url" }]` with MP3 stream URLs. Empty = built-in list |
| `playerUploads` | | Let players upload their own capes and wings for staff to review (default `true`) |
| `trustProxy` | `ASTRA_TRUST_PROXY` | Behind a tunnel or reverse proxy: rate limits use each player's real address (set automatically by `host`) |

### Put your server online (your PC + Cloudflare Tunnel)

Players' games and apps need to reach your server over the internet. Cloudflare Tunnel does that for free, with HTTPS, without opening ports on your router or showing your home IP. Players can only connect while your PC is on and the window is open.

**Try it today (test address):** double-click **`Host Astra Online.bat`**. The first time it downloads `cloudflared` (about 50 MB) into `tools/bin`, makes sure `devAuth` is off, then starts the server and prints an address like `https://some-random-words.trycloudflare.com`. Friends put that address in the app under **Settings → Astra server**. The address changes every time you run it, so it's only for testing.

**Permanent address (recommended for real players):**

1. Get a domain and add it to a free Cloudflare account (cheap domains are about $10 a year at Cloudflare Registrar, or move one you own to Cloudflare's nameservers).
2. Double-click **`Set Up Permanent Address.bat`** and type an address such as `play.yourdomain.com`. A browser opens once to sign in to Cloudflare. It creates the tunnel, points the address at it and sets `publicUrl` in `server/config.json`.
3. Run **`Build Installer.bat`**. The installer now points players at your address, and the website's **Download** button serves it (`https://play.yourdomain.com/download/Astra-Setup.exe`).
4. From then on, run **`Host Astra Online.bat`** whenever you want to be online.

**Being the Owner:** keep your Minecraft name in `owners`. Make trusted friends Moderators (they review uploads and ban) or Admins from the admin panel's **Players** tab. You can add a player by name before they've joined.

The server keeps a copy of its database for each of the last 14 days in `server/data/backups`, and `Host Astra Online.bat` starts the server again by itself if it ever stops. Still, **copy `server/data/` somewhere else** now and then. It holds every player, cosmetic, friend list and message. If you later move to a VPS, copy `server/` (with `data/` and `config.json`) and run the same commands there.

### Or: a free DuckDNS address (no domain to buy)

[DuckDNS](https://www.duckdns.org) gives you a free permanent name like `astraclient.duckdns.org` that follows your home internet. Unlike the tunnel, players connect straight to your PC, so you forward one port on your router and players can see your home IP.

1. Sign in at duckdns.org, add a subdomain and copy your token.
2. Double-click **`Set Up DuckDNS.bat`** and enter the subdomain, the token and a port (Enter for 443). It downloads [Caddy](https://caddyserver.com) into `tools/bin`, which gets a free HTTPS certificate through DuckDNS, and sets `publicUrl` to `https://yourname.duckdns.org`.
3. In your router, forward **TCP 443** (or the port you picked) to this PC. The script shows the PC's local IP.
4. Run **`Build Installer.bat`**, then **`Host Astra Online.bat`** whenever you want to be online. It also keeps DuckDNS updated if your home IP changes.

Test from your phone on mobile data: many routers can't reach their own public address from inside the house. If nothing connects at all, your internet provider may block the port (try 8443) or use CGNAT (then use the Cloudflare Tunnel instead). Running `Set Up Permanent Address.bat` later switches back to the tunnel.

### Player uploads

Signed-in players can upload their own cape or wings on the website's **Account** page (or from the app: **Cosmetics → Upload your own**). Uploads go into the admin panel's **Uploads** tab. Moderators and up approve them (the player can then wear it, and only they can) or reject them with a reason (the image is deleted). Each player can have 3 uploads waiting and keep 10 approved ones, with a 2 MB image limit. Turn uploads off with `"playerUploads": false`.

## Players and Discord

**Find players.** The website's **Players** page lists everyone in game right now and the server they're on, with a search box (Enter opens any player's profile). Profiles and the Team page show "Playing on …" too. Players can hide their server in game with **Right Shift → Cosmetics → Options → Share My Server**. Raw IP addresses and LAN servers are always shown as "a private server".

**Friends.** Friends, requests and private messages live on the Astra server, so they're the same in game (**Shift + Tab**), in the app's **Friends** tab and on the website's **Friends** page. Friends who are on a multiplayer server get a **Join** button in game. Everything arrives instantly over a WebSocket (`/api/live`): messages, friend requests, friends coming online and emotes. If the connection drops, the game, app and website reconnect by themselves and fall back to checking every few seconds meanwhile. Cloudflare Tunnel and Caddy (DuckDNS) both carry WebSockets, so nothing extra is needed when hosting.

**Your account page.** On the website, **Account** lets players write a bio for their profile, hide their server from everyone, turn friend requests off and link their Discord account. The website is opened already signed in from the app (Cosmetics → Open on website, or Friends → Messages on website). The **Admin** link only shows for staff (Moderator and up).

**Discord.** Everything is optional and set in `server/config.json`:

| Key | What it does | How to get it |
|---|---|---|
| `discordInvite` | Adds a Discord button to the website | Discord: right-click your server → Invite People → copy a link that never expires |
| `discordWebhook` | Posts new players and every staff action (ranks, bans, gifts, uploads) to a channel | Channel settings → Integrations → Webhooks → New Webhook → Copy Webhook URL |
| `discordClientId` | Shows "Minecraft 1.8.9 · On mc.hypixel.net" on players' Discord profiles while they play | [discord.com/developers](https://discord.com/developers/applications) → New Application, name it **Astra Client** → copy the Application ID. Under Rich Presence → Art Assets, upload your logo with the name `astra` |
| `discordClientSecret` | Lets players link their Discord on the Account page (needs `discordClientId` too) | Same application → OAuth2 → Reset Secret → copy it. Under OAuth2 → Redirects add `<publicUrl>/api/discord/callback`, e.g. `http://localhost:8787/api/discord/callback` |

Players can turn the Discord status off in the app's **Settings → Discord status**.

## For developers

### The quick way (Windows)

Double-click **`Start Astra.bat`** in the project folder. The first time, it installs what the app needs and asks for your Minecraft name (you become Owner). Every time, it builds the client, starts the Astra server in its own window (unless it's already running) and opens the app.

| Script | What it does |
|---|---|
| `Start Astra.bat` | Set up if needed, build, start the server, open the app |
| `Start Server Only.bat` | Just the server, on this PC only |
| `Host Astra Online.bat` | The server plus a Cloudflare Tunnel, so players anywhere can connect |
| `Set Up Permanent Address.bat` | One-time setup of `https://play.yourdomain.com` for the tunnel |
| `Set Up DuckDNS.bat` | One-time setup of a free `https://yourname.duckdns.org` address (needs a router port forward) |
| `Build Installer.bat` | Builds the client and a Windows installer for players in `launcher/dist`. Set `publicUrl` in `server/config.json` to your hosted server first: the installer points players there |

The same commands work anywhere with `node tools/astra.js [start|server|build|installer|host|address|duckdns]`.

### Prerequisites

| Tool | Version | Used for |
|---|---|---|
| JDK | 21 | Running Gradle and Loom (the client itself targets Java 8) |
| Node.js | 20+ | The Electron launcher |
| IntelliJ IDEA | any recent | Recommended for the Java side |

### Build the client

```bash
git clone https://github.com/LoyaltyVal/Astra---Minecraft-Client.git
cd Astra---Minecraft-Client
./gradlew build                        # jars: versions/<mc>/build/libs/astra-<version>+<mc>.jar
./gradlew :versions:1.8.9:runClient    # dev client without the launcher
```

In IntelliJ, open the folder and set **Settings → Build Tools → Gradle → Gradle JVM** to **Java 21**.

### Run the launcher

```bash
cd launcher
npm install
npm start          # uses the newest jars from ../versions/<mc>/build/libs
npm run dist       # builds launcher/dist/Astra Launcher Setup <version>.exe
```

During development the loop is `./gradlew build`, then **Launch**: the launcher copies the newest jar into the game every time. `npm run dist` bundles the jars into the installer.

### Add a feature

1. Create a class in `common/src/main/java/dev/astra/client/module/impl/`. Extend `TextHudModule` for a one-line readout, `HudModule` for custom drawing, or `Module` for a mechanic.
2. Add settings with `setting(new BooleanSetting(...))`, `NumberSetting`, `ColorSetting`, `KeySetting` or `ModeSetting`. They appear in the menu and save automatically.
3. Register it in `ModuleManager.registerDefaults()`.

```java
public final class SessionTimeModule extends TextHudModule {
    private final long started = System.currentTimeMillis();

    public SessionTimeModule() {
        super("Session", "How long you've been playing.", false, 4, 120);
    }

    @Override
    protected String getText() {
        long s = (System.currentTimeMillis() - started) / 1000;
        return String.format("Session: %d:%02d", s / 60, s % 60);
    }
}
```

### How versions work

Everything version-independent lives in `common/` and talks to Minecraft only through the `Platform` interface. Each `versions/<mc>/` folder holds one `PlatformImpl`, a screen wrapper and a few mixins, and those are the only files that use Minecraft names. Every name was checked against the Legacy Yarn build 604 jars for both versions. If a mappings update renames something, run `./gradlew :versions:<mc>:genSources` and look it up.

To add a Minecraft version, copy `versions/1.8.9`, change its `gradle.properties`, fix any renamed names, and add it to `settings.gradle`.

## Project structure

```
common/                  Version-independent code: features, HUD, menus, cosmetics, config
versions/1.8.9/          PlatformImpl, AstraScreen, WingsFeature and mixins for 1.8.9
versions/1.7.10/         The same for 1.7.10
gradle/version.gradle    Shared build logic for every version
launcher/src/main/       Electron main process: sign-in, Astra account, downloads, launching
launcher/src/renderer/   Launcher UI
tools/astra.js           One-step setup / build / run / installer (used by the .bat files)
server/src/              Astra server: HTTP router, JSON store, services (auth, players, ranks, cosmetics, audit), routes
server/public/           Website: home, cosmetics, team, player profiles, admin panel (one JS class per page)
server/seed/             Starter capes and wings added on first run
server/test/             API tests (node --test)
docs/images/             Screenshots for this README
```

## Roadmap

- [x] HUD features, mod menu and HUD editor
- [x] Astra Launcher with Microsoft sign-in and automatic setup
- [x] Capes and wings from your own images
- [x] Astra server: cosmetics everyone sees, ranks, website and admin panel
- [x] Friends, private messages and emotes (game, app and website)
- [x] Account switcher, account page and Discord linking
- [ ] Launcher auto-update (electron-updater with GitHub Releases)
- [ ] Code-signed installer, so Windows SmartScreen doesn't warn
- [ ] macOS and Linux builds

## License

All rights reserved. Astra has no open-source license yet, so you may read the code but not redistribute it or publish modified versions without permission.

Astra is not an official Minecraft product and is not approved by or associated with Mojang or Microsoft.
