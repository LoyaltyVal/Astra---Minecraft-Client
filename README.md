# Astra Client

A PvP client for Minecraft **1.8.9** and **1.7.10**, built on Legacy Fabric, with its own launcher.

## Mods

Press **Right Shift** in game to open the menu.

| Mod | What it does | Default |
|---|---|---|
| FPS / CPS | Frames and clicks per second | On |
| Keystrokes | WASD, mouse buttons with CPS, and space, with press fades | On |
| Scoreboard | Movable sidebar; hide the red numbers, change opacity | On |
| Armor Status | Armor and held item with durability (value or percent) | On |
| Potion Effects | Active effects with icons and time left; blinks when ending | On |
| Boss Bar | Move or resize the wither/dragon bar | On |
| Toggle Sprint | Tap Left Ctrl to toggle sprinting | On |
| Zoom | Hold **C**; smooth, lowers mouse sensitivity while zoomed | On |
| Direction HUD | Compass strip with degrees | Off |
| Coordinates | XYZ and facing | Off |
| Ping / Speed | Latency to the server; blocks per second | Off |
| Clock / Memory | Local time; RAM use | Off |
| Fullbright | Max brightness; restores your gamma when turned off | Off |

**In-game menu**
- **MODS** shows a card per mod with a live preview. Click a card to toggle it, and click **OPTIONS** (or right-click the card) for its settings. Tabs and search filter the list.
- **COSMETICS** lists your capes and wings. Click one to equip it.
- The **grid button** (or the eye in the corner) opens the HUD editor. Drag mods to move them (they snap to edges and center lines), scroll over one to resize it, and right-click one for its settings.
- The **gear** opens client settings: accent color, background dim, editor labels.
- Everything saves to `config/astra.json` in the game folder.

## Cosmetics: capes & wings

Cosmetics are image files in `%APPDATA%\.astra\cosmetics\capes` and `...\wings`. Add them from the launcher's **Cosmetics** tab (**Add images**), or drop files into those folders. Equip them in the launcher or in game; both stay in sync.

**Capes**: any picture works. Astra crops it to the cape's shape (10:16, portrait) and wraps it onto the cape. A standard cape template (64×32, or HD 128×64, 256×128, and so on) is used as-is.

**Wings**: one wing with its root on the **left** and the tip on the **right**, or a **pair** side by side. A plain background (like white) is removed automatically, but a transparent PNG gives the cleanest edges. If a wing comes out backwards, set **Wing Image** in Cosmetics → Options. Wing size and flap speed are there too.

Tips for generating art with an AI image tool:
- Cape: *"A vertical banner design, 10:16 portrait, [your theme], flat bold colors, no text, fills the whole image."*
- Wing: *"A single [angel/dragon/feather] wing, side view, attached on the left and extending right, centered, on a plain white background, no shadow."*
- Avoid tiny text and detail; the cape is only 10×16 Minecraft pixels on screen.

**Who can see them?** Right now cosmetics show on **your own player**, in third person (F5) and in your inventory. For other Astra players to see each other's cosmetics, the client needs a small cosmetics server that shares who's wearing what. The code is set up for that: `CosmeticsManager` is the one place to plug it in.

## Launcher

`launcher/` holds the Astra Launcher, an Electron app. It handles Microsoft sign-in and downloads Java 8, Minecraft and Legacy Fabric. Then it installs Astra and launches the game. It also has a Cosmetics tab, game logs (saved to `%APPDATA%\Astra Launcher\logs`) and settings for RAM, Java and hiding while playing. Game files go in `%APPDATA%\.astra`, so your normal `.minecraft` folder is never touched.

You need [Node.js](https://nodejs.org) 20 or newer.

```
cd launcher
npm install
npm start            # run the launcher (uses the jars from `gradlew build`)
npm run dist         # build the installer: launcher/dist/Astra Launcher Setup <version>.exe
```

During development the launcher picks up the newest jar from `versions/<mc>/build/libs`, so your loop is `gradlew build`, then Launch. `npm run dist` packs those jars and the starter cosmetics into the installer.

**Before you share it publicly:**
- **Code signing.** Without a code-signing certificate, Windows SmartScreen warns on first run. Signing needs a certificate from a CA; electron-builder signs automatically once one is configured.
- **Auto-update.** The launcher doesn't update itself yet. electron-updater with a GitHub Releases or S3 feed is the usual next step.

## Building the mod

1. Open this folder in IntelliJ.
2. Set **Settings → Build Tools → Gradle → Gradle JVM** to **Java 21**. Loom needs it; the mod itself still targets Java 8.
3. Run the build:

```
gradlew build                          # jars land in versions/<mc>/build/libs/
gradlew :versions:1.8.9:runClient      # dev client without the launcher
```

## Project layout

```
common/                 Version-independent code: mods, HUD, menus, cosmetics, config
versions/1.8.9/         PlatformImpl, AstraScreen, WingsFeature + mixins for 1.8.9
versions/1.7.10/        The same for 1.7.10
gradle/version.gradle   Shared build logic for every version
launcher/src/main/      Electron main process: sign-in, downloads, launching, cosmetics
launcher/src/renderer/  Launcher UI
launcher/resources/     Starter capes and wings bundled with the installer
launcher/build/         App icon
```

The shared code only talks to Minecraft through the `Platform` interface. Every Minecraft name lives in `versions/<mc>/`, and all of them were checked against the real Legacy Yarn build 604 jars for both versions. If a mappings update renames something, run `gradlew :versions:<mc>:genSources` and look it up.

**Adding a mod:**
1. Make a class in `common/.../module/impl/`. Extend `TextHudModule` for a one-line readout, `HudModule` for custom drawing, or `Module` for a mechanic.
2. Add settings with `setting(new BooleanSetting(...))` and the other setting types. They show up in the menu and save automatically.
3. Register it in `ModuleManager.registerDefaults()`.

Stick to visual and quality-of-life mods. Anything that gives a gameplay advantage (reach, autoclicker, x-ray) gets players banned on Hypixel and most servers.
