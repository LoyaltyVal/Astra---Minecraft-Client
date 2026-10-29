// Launcher configuration: every Minecraft version Astra knows about, and where its loader comes from.
//
// A version only shows up in the launcher once an Astra build exists for it (versions/<mc> built by
// Gradle, or launcher/mods/<mc>/astra.jar in the installer); see mods.availableVersions().

/** Fabric Loader version for every game version (Legacy Fabric uses the same loader). Keep in sync with gradle.properties. */
const LOADER_VERSION = '0.18.3';

/** Legacy Fabric (1.3 - 1.13.2) and Fabric (1.14+) metadata servers. Same API. */
const LEGACY_FABRIC_META = 'https://meta.legacyfabric.net/v2';
const FABRIC_META = 'https://meta.fabricmc.net/v2';

/** Mojang's list of every Minecraft version (each one says which Java it needs). */
const VERSION_MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';

/** Mojang's Java runtime index (used by the official launcher). */
const JAVA_RUNTIME_INDEX =
  'https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json';

/** Modrinth's API, for Fabric API on modern versions. */
const MODRINTH_API = 'https://api.modrinth.com/v2';

/**
 * Every release Astra targets, newest first, grouped the way players think about them.
 * Older lines list only the patch everyone plays; newer ones list each release.
 */
const FAMILIES = [
  { family: '26.3', versions: ['26.3'], subtitle: 'The newest Minecraft.' },
  { family: '26.2', versions: ['26.2'] },
  { family: '26.1', versions: ['26.1.2', '26.1.1', '26.1'] },
  {
    family: '1.21',
    versions: ['1.21.11', '1.21.10', '1.21.9', '1.21.8', '1.21.7', '1.21.6', '1.21.5', '1.21.4', '1.21.3', '1.21.1', '1.21'],
    subtitle: 'Modern PvP and most of today’s servers.'
  },
  { family: '1.20', versions: ['1.20.6', '1.20.4', '1.20.2', '1.20.1'] },
  { family: '1.19', versions: ['1.19.4', '1.19.2'] },
  { family: '1.18', versions: ['1.18.2'] },
  { family: '1.17', versions: ['1.17.1'] },
  { family: '1.16', versions: ['1.16.5'], subtitle: 'The last version before caves & cliffs.' },
  { family: '1.15', versions: ['1.15.2'] },
  { family: '1.14', versions: ['1.14.4'] },
  { family: '1.13', versions: ['1.13.2'] },
  { family: '1.12', versions: ['1.12.2'], subtitle: 'The classic modded version.' },
  { family: '1.11', versions: ['1.11.2'] },
  { family: '1.10', versions: ['1.10.2'] },
  { family: '1.9', versions: ['1.9.4'] },
  { family: '1.8', versions: ['1.8.9'], subtitle: 'The PvP standard. Hypixel, Minemen and most practice servers.' },
  { family: '1.7', versions: ['1.7.10'], subtitle: 'Old-school combat with classic animations.' }
];

/** Compares Minecraft version ids numerically ("1.21.10" > "1.21.9", "26.1" > "1.21.11"). */
function compareVersions(a, b) {
  const pa = String(a).split('.').map((n) => parseInt(n, 10) || 0);
  const pb = String(b).split('.').map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] || 0) - (pb[i] || 0);
    if (d) return d;
  }
  return 0;
}

/** 1.13.2 and older run on Legacy Fabric; 1.14 and newer on Fabric. */
function isLegacy(id) {
  return compareVersions(id, '1.14') < 0;
}

/**
 * The Java Mojang ships for a version. Only used when Mojang's version list can't be reached;
 * the real value is read from Mojang (java.javaComponentFor).
 */
function fallbackJavaComponent(id) {
  if (compareVersions(id, '1.17') < 0) return 'jre-legacy'; // Java 8
  if (compareVersions(id, '1.18') < 0) return 'java-runtime-alpha'; // Java 16
  if (compareVersions(id, '1.20.5') < 0) return 'java-runtime-gamma'; // Java 17
  if (compareVersions(id, '26.1') < 0) return 'java-runtime-delta'; // Java 21
  return 'java-runtime-epsilon'; // Java 25
}

function familyOf(id) {
  return FAMILIES.find((f) => f.versions.includes(id)) || null;
}

/** Everything the launcher needs to know about one version id (known or not). */
function describe(id) {
  const fam = familyOf(id);
  const parts = id.split('.');
  return {
    id,
    title: id,
    family: fam ? fam.family : parts.slice(0, parts[0] === '1' ? 2 : 1).join('.'),
    subtitle: (fam && fam.subtitle) || (isLegacy(id) ? 'Runs on Legacy Fabric.' : 'Runs on Fabric.'),
    legacy: isLegacy(id),
    // Modern Astra builds use Fabric API; on Legacy Fabric what Astra needs is inside its own jar.
    fabricApi: !isLegacy(id),
    javaComponent: fallbackJavaComponent(id)
  };
}

/** Every release id Astra targets, newest first. */
const KNOWN_VERSIONS = FAMILIES.flatMap((f) => f.versions);

function getVersion(id) {
  return typeof id === 'string' && /^\d+(\.\d+){1,2}$/.test(id) ? describe(id) : null;
}

module.exports = {
  LOADER_VERSION,
  LEGACY_FABRIC_META,
  FABRIC_META,
  VERSION_MANIFEST,
  JAVA_RUNTIME_INDEX,
  MODRINTH_API,
  FAMILIES,
  KNOWN_VERSIONS,
  compareVersions,
  isLegacy,
  fallbackJavaComponent,
  describe,
  getVersion
};
