const fs = require('fs');
const path = require('path');
const { LEGACY_FABRIC_META, LOADER_VERSION } = require('./config');
const { fetchJson } = require('./download');
const paths = require('./paths');

/**
 * Installs the Legacy Fabric loader profile for a game version and returns the custom
 * version id to hand to minecraft-launcher-core (e.g. "astra-fabric-1.8.9").
 *
 * The profile JSON from Legacy Fabric lists the loader + intermediary libraries (with maven URLs)
 * and the Knot main class; minecraft-launcher-core downloads those libraries itself.
 */
async function ensureLoader(gameVersion, onProgress) {
  const id = `astra-fabric-${gameVersion}`;
  const dir = path.join(paths.versionsDir(), id);
  const file = path.join(dir, `${id}.json`);

  const existing = readJson(file);
  if (existing && existing.astraLoaderVersion === LOADER_VERSION && existing.astraProfileRevision === PROFILE_REVISION) {
    return id;
  }

  onProgress({ label: `Installing Legacy Fabric ${LOADER_VERSION}`, current: 0, total: 1 });
  const url = `${LEGACY_FABRIC_META}/versions/loader/${encodeURIComponent(gameVersion)}/${encodeURIComponent(LOADER_VERSION)}/profile/json`;

  let profile;
  try {
    profile = await fetchJson(url);
  } catch (e) {
    if (existing) {
      return id; // offline but an older loader is installed: keep playing
    }
    throw new Error(`Couldn't download Legacy Fabric for ${gameVersion} (${e.message}).`);
  }

  profile.id = id;
  profile.astraLoaderVersion = LOADER_VERSION;
  profile.astraProfileRevision = PROFILE_REVISION;
  profile.libraries = (profile.libraries || []).filter(keepLibrary);
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(file, JSON.stringify(profile, null, 2));
  onProgress({ label: `Installing Legacy Fabric ${LOADER_VERSION}`, current: 1, total: 1 });
  return id;
}

/** Bump when the way profiles are adapted changes, so existing installs get rewritten. */
const PROFILE_REVISION = 2;

/**
 * Legacy Fabric's profile replaces Minecraft's LWJGL 2 with its own build, including a
 * natives-only "lwjgl-platform" library. minecraft-launcher-core can't handle natives in custom
 * profiles: it downloads the (non-existent) plain jar, saves the 404 page as a .jar, and Fabric
 * crashes reading it. On Java 8 the vanilla LWJGL that Minecraft already ships works fine, so we
 * drop the overrides and let the vanilla libraries + natives be used.
 */
function keepLibrary(lib) {
  if (!lib || !lib.name) return false;
  if (lib.natives) return false;
  return !/^org\.lwjgl(\.lwjgl)?:/.test(lib.name);
}

function readJson(file) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return null;
  }
}

module.exports = { ensureLoader };
