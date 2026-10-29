// Copies the built Astra jars (from `gradlew build`) into launcher/mods/<mc>/astra.jar
// so they get packed into the installer. Run automatically by `npm run dist`.
//
// Every version folder in versions/ is included, and only the jar of the current mod_version
// (gradle.properties), so an old build lying around can never end up in the installer.
const fs = require('fs');
const path = require('path');

const projectDir = path.join(__dirname, '..', '..');
const outDir = path.join(__dirname, '..', 'mods');

const props = fs.readFileSync(path.join(projectDir, 'gradle.properties'), 'utf8');
const modVersion = ((/^mod_version=(.+)$/m.exec(props) || [])[1] || '').trim();
if (!modVersion) {
  console.error('mod_version is missing from gradle.properties.');
  process.exit(1);
}

const versions = fs
  .readdirSync(path.join(projectDir, 'versions'), { withFileTypes: true })
  .filter((e) => e.isDirectory() && /^\d+(\.\d+){1,2}$/.test(e.name))
  .map((e) => e.name);

// Start clean: a version that isn't built this time must not keep an old jar in the installer.
fs.rmSync(outDir, { recursive: true, force: true });

let copied = 0;
const missing = [];
for (const mc of versions) {
  const jar = path.join(projectDir, 'versions', mc, 'build', 'libs', `astra-${modVersion}+${mc}.jar`);
  if (!fs.existsSync(jar)) {
    missing.push(mc);
    continue;
  }
  fs.mkdirSync(path.join(outDir, mc), { recursive: true });
  fs.copyFileSync(jar, path.join(outDir, mc, 'astra.jar'));
  console.log(`✓ ${mc}: ${path.basename(jar)}`);
  copied++;
}

if (missing.length) {
  console.error(`No Astra ${modVersion} build for ${missing.join(', ')}. Run "gradlew build" first.`);
  process.exit(1);
}
if (!copied) {
  console.error('No Astra jars found; the installer would launch without the mod.');
  process.exit(1);
}
