const fs = require('fs');
const path = require('path');
const https = require('https');
const { execSync } = require('child_process');

/**
 * Parses a Java .properties file into a key-value object.
 */
function parsePropertiesFile(filePath) {
  if (!fs.existsSync(filePath)) return {};
  const content = fs.readFileSync(filePath, 'utf8');
  const result = {};
  content.split('\n').forEach(line => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) return;
    const idx = trimmed.indexOf('=');
    if (idx !== -1) {
      const key = trimmed.substring(0, idx).trim();
      const val = trimmed.substring(idx + 1).trim();
      result[key] = val;
    }
  });
  return result;
}

/**
 * Extracts semantic version components (major, minor, patch) from a tag or string.
 */
function parseSemVer(str) {
  if (!str) return null;
  const match = str.match(/v?([0-9]+)\.([0-9]+)(?:\.([0-9]+))?/);
  if (!match) return null;
  return {
    major: parseInt(match[1], 10),
    minor: parseInt(match[2], 10),
    patch: match[3] !== undefined ? parseInt(match[3], 10) : 0
  };
}

/**
 * Extracts versionCode from release notes or title.
 */
function extractVersionCodeFromText(text) {
  if (!text) return null;
  const match = text.match(/(?:versionCode|build)[\s:=]+([0-9]+)/i);
  return match ? parseInt(match[1], 10) : null;
}

/**
 * Fetches JSON from a URL using HTTPS.
 */
function fetchJson(url, token = null) {
  return new Promise((resolve, reject) => {
    const parsedUrl = new URL(url);
    const headers = {
      'User-Agent': 'LiveWaterPaper-VersionManager',
      'Accept': 'application/vnd.github.v3+json'
    };
    if (token) {
      headers['Authorization'] = `token ${token}`;
    }

    const req = https.get(parsedUrl, { headers, timeout: 15000 }, res => {
      if (res.statusCode === 404) {
        return resolve([]);
      }
      if (res.statusCode < 200 || res.statusCode >= 300) {
        return reject(new Error(`GitHub API returned HTTP ${res.statusCode}: ${res.statusMessage}`));
      }

      let data = '';
      res.on('data', chunk => { data += chunk; });
      res.on('end', () => {
        try {
          const json = JSON.parse(data);
          resolve(json);
        } catch (e) {
          reject(e);
        }
      });
    });

    req.on('error', reject);
    req.on('timeout', () => {
      req.destroy();
      reject(new Error('Request timed out'));
    });
  });
}

/**
 * Gets local git tags matching "v*".
 */
function getLocalGitTags() {
  try {
    const output = execSync('git tag -l "v*"', { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
    return output.split('\n').map(t => t.trim()).filter(Boolean);
  } catch (e) {
    return [];
  }
}

/**
 * Core version resolution calculation.
 * Guaranteed to be strictly monotonic and unique.
 */
function resolveNextVersion({
  baselineMajor = 1,
  baselineMinor = 0,
  baselinePatch = 0,
  minimumVersionCode = 1,
  existingReleases = [],
  existingTags = []
}) {
  let highestVersionCode = minimumVersionCode;
  let highestReleasedMajor = 0;
  let highestReleasedMinor = 0;
  let highestReleasedPatch = 0;
  let hasExistingReleasesOrTags = false;

  const knownTags = new Set(existingTags);

  // 1. Analyze existing GitHub releases
  if (Array.isArray(existingReleases)) {
    for (const rel of existingReleases) {
      if (!rel) continue;
      if (rel.tag_name) knownTags.add(rel.tag_name);

      // Check versionCode in release name, body, or tag
      let code = extractVersionCodeFromText(rel.name) || extractVersionCodeFromText(rel.body);
      if (code && code > highestVersionCode) {
        highestVersionCode = code;
      }

      const semver = parseSemVer(rel.tag_name);
      if (semver) {
        hasExistingReleasesOrTags = true;
        if (semver.major > highestReleasedMajor ||
            (semver.major === highestReleasedMajor && semver.minor > highestReleasedMinor) ||
            (semver.major === highestReleasedMajor && semver.minor === highestReleasedMinor && semver.patch > highestReleasedPatch)) {
          highestReleasedMajor = semver.major;
          highestReleasedMinor = semver.minor;
          highestReleasedPatch = semver.patch;
        }
      }
    }
  }

  // 2. Analyze known git tags
  for (const tag of knownTags) {
    const semver = parseSemVer(tag);
    if (semver) {
      hasExistingReleasesOrTags = true;
      if (semver.major > highestReleasedMajor ||
          (semver.major === highestReleasedMajor && semver.minor > highestReleasedMinor) ||
          (semver.major === highestReleasedMajor && semver.minor === highestReleasedMinor && semver.patch > highestReleasedPatch)) {
        highestReleasedMajor = semver.major;
        highestReleasedMinor = semver.minor;
        highestReleasedPatch = semver.patch;
      }
    }
  }

  // 3. Compute new versionCode (strictly monotonic)
  const newVersionCode = highestVersionCode + 1;

  // 4. Compute new versionName
  let targetMajor = baselineMajor;
  let targetMinor = baselineMinor;
  let nextPatch = baselinePatch;

  if (hasExistingReleasesOrTags) {
    if (baselineMajor > highestReleasedMajor) {
      // Manual major upgrade (e.g. 1.x -> 2.0.0)
      targetMajor = baselineMajor;
      targetMinor = baselineMinor;
      nextPatch = baselinePatch;
    } else if (baselineMajor === highestReleasedMajor && baselineMinor > highestReleasedMinor) {
      // Manual minor upgrade (e.g. 1.0.x -> 1.1.0)
      targetMajor = baselineMajor;
      targetMinor = baselineMinor;
      nextPatch = baselinePatch;
    } else {
      // Standard automated patch bump
      targetMajor = highestReleasedMajor;
      targetMinor = highestReleasedMinor;
      nextPatch = highestReleasedPatch + 1;
    }
  } else {
    // Initial release: first release after baseline
    targetMajor = baselineMajor;
    targetMinor = baselineMinor;
    nextPatch = baselinePatch + 1;
  }

  let newVersionName = `${targetMajor}.${targetMinor}.${nextPatch}`;
  let newTagName = `v${newVersionName}`;

  // 5. Ensure tag uniqueness (if v1.0.1 tag exists, bump patch to v1.0.2)
  while (knownTags.has(newTagName)) {
    nextPatch++;
    newVersionName = `${targetMajor}.${targetMinor}.${nextPatch}`;
    newTagName = `v${newVersionName}`;
  }

  // 6. Strict validation asserts
  if (newVersionCode <= highestVersionCode) {
    throw new Error(`Monotonicity violation: newVersionCode (${newVersionCode}) <= highestVersionCode (${highestVersionCode})`);
  }
  if (newVersionCode <= minimumVersionCode) {
    throw new Error(`Minimum version violation: newVersionCode (${newVersionCode}) <= minimumVersionCode (${minimumVersionCode})`);
  }

  return {
    previousVersionCode: highestVersionCode,
    versionCode: newVersionCode,
    versionName: newVersionName,
    tagName: newTagName
  };
}

/**
 * Main execution entry point.
 */
async function main() {
  const rootDir = path.resolve(__dirname, '..');
  const propsPath = path.join(rootDir, 'android', 'version.properties');
  const baseProps = parsePropertiesFile(propsPath);

  const baselineMajor = parseInt(baseProps.MAJOR || '1', 10);
  const baselineMinor = parseInt(baseProps.MINOR || '0', 10);
  const baselinePatch = parseInt(baseProps.PATCH || '0', 10);
  const minimumVersionCode = parseInt(baseProps.MINIMUM_VERSION_CODE || '1', 10);

  const token = process.env.GITHUB_TOKEN || null;
  const repo = process.env.GITHUB_REPOSITORY || 'vaibhav963963-netizen/water';

  console.log(`[VersionManager] Analyzing repository: ${repo}`);
  console.log(`[VersionManager] Base configuration: v${baselineMajor}.${baselineMinor}.${baselinePatch} (minCode: ${minimumVersionCode})`);

  let existingReleases = [];
  try {
    const apiUrl = `https://api.github.com/repos/${repo}/releases?per_page=100`;
    existingReleases = await fetchJson(apiUrl, token);
    console.log(`[VersionManager] Fetched ${existingReleases.length} existing releases from GitHub API.`);
  } catch (err) {
    console.warn(`[VersionManager] Could not fetch GitHub releases (${err.message}). Falling back to local git tags.`);
  }

  const existingTags = getLocalGitTags();
  console.log(`[VersionManager] Found ${existingTags.length} local git tags.`);

  const versionData = resolveNextVersion({
    baselineMajor,
    baselineMinor,
    baselinePatch,
    minimumVersionCode,
    existingReleases,
    existingTags
  });

  console.log('----------------------------------------------------');
  console.log(`[VersionManager] Previous Version Code : ${versionData.previousVersionCode}`);
  console.log(`[VersionManager] NEW Version Code      : ${versionData.versionCode} (+1 guaranteed monotonic)`);
  console.log(`[VersionManager] NEW Version Name      : ${versionData.versionName}`);
  console.log(`[VersionManager] Target Git Release Tag: ${versionData.tagName}`);
  console.log('----------------------------------------------------');

  // Write android/app/build-version.properties for Gradle
  const appDir = path.join(rootDir, 'android', 'app');
  const buildVersionPropsPath = path.join(appDir, 'build-version.properties');
  const propsContent = `# Automatically generated by version-manager.js\nversionCode=${versionData.versionCode}\nversionName=${versionData.versionName}\n`;
  fs.writeFileSync(buildVersionPropsPath, propsContent, 'utf8');
  console.log(`[VersionManager] Written ${buildVersionPropsPath}`);

  // Write version.json in root
  const jsonPath = path.join(rootDir, 'version.json');
  fs.writeFileSync(jsonPath, JSON.stringify(versionData, null, 2), 'utf8');

  // Export to GITHUB_OUTPUT if present
  if (process.env.GITHUB_OUTPUT) {
    const githubOutput = [
      `version_code=${versionData.versionCode}`,
      `version_name=${versionData.versionName}`,
      `tag_name=${versionData.tagName}`,
      `previous_version_code=${versionData.previousVersionCode}`
    ].join('\n') + '\n';
    fs.appendFileSync(process.env.GITHUB_OUTPUT, githubOutput, 'utf8');
    console.log('[VersionManager] Appended version outputs to $GITHUB_OUTPUT');
  }

  return versionData;
}

if (require.main === module) {
  main().catch(err => {
    console.error('[VersionManager] FATAL ERROR:', err);
    process.exit(1);
  });
}

module.exports = {
  parsePropertiesFile,
  parseSemVer,
  extractVersionCodeFromText,
  resolveNextVersion
};
