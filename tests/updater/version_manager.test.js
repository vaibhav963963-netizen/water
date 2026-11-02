const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { resolveNextVersion, parseSemVer, extractVersionCodeFromText } = require('../../scripts/version-manager');

describe('Automatic Android Version Manager', () => {

  it('correctly parses semantic version strings', () => {
    assert.deepEqual(parseSemVer('v1.0.0'), { major: 1, minor: 0, patch: 0 });
    assert.deepEqual(parseSemVer('1.2.3'), { major: 1, minor: 2, patch: 3 });
    assert.deepEqual(parseSemVer('v2.0'), { major: 2, minor: 0, patch: 0 });
    assert.equal(parseSemVer('invalid-tag'), null);
  });

  it('extracts versionCode from release names and markdown bodies', () => {
    assert.equal(extractVersionCodeFromText('Live WaterPaper v1.0.1 (Build 42)'), 42);
    assert.equal(extractVersionCodeFromText('versionCode: 7'), 7);
    assert.equal(extractVersionCodeFromText('build=15'), 15);
    assert.equal(extractVersionCodeFromText('Nothing here'), null);
  });

  it('fresh repository: increments from baseline (Build 1 -> Build 2, v1.0.0 -> v1.0.1)', () => {
    const result = resolveNextVersion({
      baselineMajor: 1,
      baselineMinor: 0,
      baselinePatch: 0,
      minimumVersionCode: 1,
      existingReleases: [],
      existingTags: []
    });

    assert.equal(result.previousVersionCode, 1);
    assert.equal(result.versionCode, 2, 'Next code must be 2');
    assert.equal(result.versionName, '1.0.1');
    assert.equal(result.tagName, 'v1.0.1');
    assert.ok(result.versionCode > result.previousVersionCode);
  });

  it('existing releases: increments from maximum released versionCode and patch', () => {
    const existingReleases = [
      { tag_name: 'v1.0.1', name: 'Build 2', body: 'First release' },
      { tag_name: 'v1.0.2', name: 'Build 3', body: 'Second release' },
      { tag_name: 'v1.0.3', name: 'Build 4', body: 'Third release' }
    ];

    const result = resolveNextVersion({
      baselineMajor: 1,
      baselineMinor: 0,
      baselinePatch: 0,
      minimumVersionCode: 1,
      existingReleases,
      existingTags: ['v1.0.1', 'v1.0.2', 'v1.0.3']
    });

    assert.equal(result.previousVersionCode, 4);
    assert.equal(result.versionCode, 5, 'Next code must be strictly 5');
    assert.equal(result.versionName, '1.0.4');
    assert.equal(result.tagName, 'v1.0.4');
    assert.ok(result.versionCode > result.previousVersionCode);
  });

  it('unordered releases: always finds the true maximum versionCode', () => {
    const existingReleases = [
      { tag_name: 'v1.0.5', name: 'Build 9', body: '' },
      { tag_name: 'v1.0.2', name: 'Build 3', body: '' },
      { tag_name: 'v1.0.8', name: 'Build 14', body: '' },
      { tag_name: 'v1.0.3', name: 'Build 5', body: '' }
    ];

    const result = resolveNextVersion({
      baselineMajor: 1,
      baselineMinor: 0,
      baselinePatch: 0,
      minimumVersionCode: 1,
      existingReleases,
      existingTags: []
    });

    assert.equal(result.previousVersionCode, 14);
    assert.equal(result.versionCode, 15);
    assert.ok(result.versionCode > 14);
  });

  it('tag collision avoidance: advances patch if tag already exists in repository', () => {
    const result = resolveNextVersion({
      baselineMajor: 1,
      baselineMinor: 0,
      baselinePatch: 0,
      minimumVersionCode: 1,
      existingReleases: [],
      existingTags: ['v1.0.1', 'v1.0.2'] // Someone manually created v1.0.1 and v1.0.2
    });

    assert.equal(result.versionCode, 2);
    assert.equal(result.versionName, '1.0.3', 'Should jump to v1.0.3 to avoid tag collision');
    assert.equal(result.tagName, 'v1.0.3');
  });

  it('major/minor bump: retains monotonic versionCode while resetting patch to 0', () => {
    const existingReleases = [
      { tag_name: 'v1.0.9', name: 'Build 10', body: '' }
    ];

    // Developer manually sets MAJOR=2, MINOR=0 in version.properties
    const result = resolveNextVersion({
      baselineMajor: 2,
      baselineMinor: 0,
      baselinePatch: 0,
      minimumVersionCode: 1,
      existingReleases,
      existingTags: ['v1.0.9']
    });

    assert.equal(result.previousVersionCode, 10);
    assert.equal(result.versionCode, 11, 'versionCode must continue increasing');
    assert.equal(result.versionName, '2.0.0', 'versionName transitions to 2.0.0');
    assert.equal(result.tagName, 'v2.0.0');
  });

  it('monotonicity assertion: guarantees versionCode is strictly greater than 1 and previous', () => {
    for (let current = 1; current < 50; current++) {
      const res = resolveNextVersion({
        baselineMajor: 1,
        baselineMinor: 0,
        baselinePatch: 0,
        minimumVersionCode: 1,
        existingReleases: [{ tag_name: `v1.0.${current}`, name: `Build ${current}`, body: '' }],
        existingTags: []
      });
      assert.ok(res.versionCode > current, `Expected ${res.versionCode} > ${current}`);
      assert.ok(res.versionCode > 1);
    }
  });

});
