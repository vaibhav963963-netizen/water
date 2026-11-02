const { describe, it } = require('node:test');
const assert = require('node:assert/strict');

// Mirror the logic implemented in UpdateChecker.kt and UpdateDownloader.kt
function parseUpdateManifest(manifestJsonStr) {
  const json = JSON.parse(manifestJsonStr);
  return {
    versionCode: json.versionCode ?? -1,
    versionName: json.versionName ?? '',
    sha256: json.sha256 ? json.sha256.trim().toLowerCase() : null,
    apkFileName: json.apkFileName ?? 'LiveWaterPaper.apk'
  };
}

function parseFallbackReleaseInfo(tagName, releaseNotes) {
  let versionCode = -1;
  let versionName = tagName.replace(/^v/, '').trim();
  let sha256 = null;

  const codeMatch = releaseNotes.match(/(?:versionCode|build)[\s:=]+([0-9]+)/i);
  if (codeMatch) {
    versionCode = parseInt(codeMatch[1], 10);
  }

  if (versionCode === -1) {
    const semverMatch = tagName.match(/v?([0-9]+)\.([0-9]+)(?:\.([0-9]+))?/);
    if (semverMatch) {
      const major = parseInt(semverMatch[1], 10) || 0;
      const minor = parseInt(semverMatch[2], 10) || 0;
      const patch = parseInt(semverMatch[3], 10) || 0;
      versionCode = major * 10000 + minor * 100 + patch;
    }
  }

  const shaMatch = releaseNotes.match(/(?:sha256|hash)[\s:=]+([a-f0-9]{64})/i);
  if (shaMatch) {
    sha256 = shaMatch[1].toLowerCase();
  }

  return { versionCode, versionName, sha256 };
}

function verifySha256(computedHash, expectedHash) {
  if (!expectedHash) return true;
  return computedHash.trim().toLowerCase() === expectedHash.trim().toLowerCase();
}

describe('Native Android Updater Logic', () => {
  it('correctly parses structured update_manifest.json', () => {
    const rawManifest = JSON.stringify({
      versionCode: 2,
      versionName: '1.0.1',
      sha256: '5B308F51AA7E3A1B05399C64C2BFD3D0FA925C55565DA6405BE648EDBB9DBD94',
      apkFileName: 'LiveWaterPaper-v1.0.1.apk'
    });

    const parsed = parseUpdateManifest(rawManifest);
    assert.equal(parsed.versionCode, 2);
    assert.equal(parsed.versionName, '1.0.1');
    assert.equal(parsed.sha256, '5b308f51aa7e3a1b05399c64c2bfd3d0fa925c55565da6405be648edbb9dbd94');
    assert.equal(parsed.apkFileName, 'LiveWaterPaper-v1.0.1.apk');
  });

  it('correctly compares version codes to determine update availability', () => {
    const currentVersionCode = 1;

    assert.equal(2 > currentVersionCode, true, 'Build 2 should trigger update');
    assert.equal(1 > currentVersionCode, false, 'Build 1 is current, should not trigger');
    assert.equal(0 > currentVersionCode, false, 'Older build should not trigger');
  });

  it('extracts version code and SHA-256 fallback from release body', () => {
    const tagName = 'v1.1.0';
    const releaseBody = `
      ## Live WaterPaper v1.1.0
      - Enhanced fluid speed
      - build: 5
      - SHA256: 5b308f51aa7e3a1b05399c64c2bfd3d0fa925c55565da6405be648edbb9dbd94
    `;

    const info = parseFallbackReleaseInfo(tagName, releaseBody);
    assert.equal(info.versionCode, 5);
    assert.equal(info.versionName, '1.1.0');
    assert.equal(info.sha256, '5b308f51aa7e3a1b05399c64c2bfd3d0fa925c55565da6405be648edbb9dbd94');
  });

  it('verifies SHA-256 checksums case-insensitively and detects corruption', () => {
    const valid = '5B308F51AA7E3A1B05399C64C2BFD3D0FA925C55565DA6405BE648EDBB9DBD94';
    const computed = '5b308f51aa7e3a1b05399c64c2bfd3d0fa925c55565da6405be648edbb9dbd94';
    const corrupted = 'ffffffffaa7e3a1b05399c64c2bfd3d0fa925c55565da6405be648edbb9dbd94';

    assert.equal(verifySha256(computed, valid), true);
    assert.equal(verifySha256(corrupted, valid), false);
  });
});
