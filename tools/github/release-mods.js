// Used by the release workflow: copies each built Astra jar to release-mods/astra-<mc>.jar and
// writes release-mods/astra-mods.json, the list launchers read to download the newest Astra.
//   node tools/github/release-mods.js <mod_version> <tag>
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const [modVersion, tag] = process.argv.slice(2);
if (!modVersion || !tag) {
  console.error('Usage: node tools/github/release-mods.js <mod_version> <tag>');
  process.exit(1);
}
const root = path.join(__dirname, '..', '..');
const out = path.join(root, 'release-mods');
fs.mkdirSync(out, { recursive: true });

const mods = {};
for (const mc of fs.readdirSync(path.join(root, 'versions')).sort()) {
  const jar = path.join(root, 'versions', mc, 'build', 'libs', `astra-${modVersion}+${mc}.jar`);
  if (!fs.existsSync(jar)) continue;
  const file = `astra-${mc}.jar`;
  const data = fs.readFileSync(jar);
  fs.writeFileSync(path.join(out, file), data);
  // "built" lets a launcher skip this when the copy it came with is newer (a local installer build).
  mods[mc] = { file, sha1: crypto.createHash('sha1').update(data).digest('hex'), size: data.length, built: Math.round(fs.statSync(jar).mtimeMs) };
  console.log(`${mc}: ${file}`);
}
if (!Object.keys(mods).length) {
  console.error('No Astra jars were built.');
  process.exit(1);
}
fs.writeFileSync(path.join(out, 'astra-mods.json'), JSON.stringify({ tag, mods }, null, 2) + '\n');
