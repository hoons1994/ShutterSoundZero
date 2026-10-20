import assert from 'node:assert/strict';
import test from 'node:test';
import { verifyDependencyReview } from './verify-dependency-review.mjs';

const options = { repository: 'owner/repo', baseSha: 'a'.repeat(40), headSha: 'b'.repeat(40), token: 'test-token' };
const endpoint = `https://api.github.com/repos/owner/repo/dependency-graph/compare/${options.baseSha}...${options.headSha}`;
const change = (overrides = {}) => ({
  change_type: 'added', manifest: 'settings.gradle.kts', ecosystem: 'maven', name: 'example:library',
  version: '1', package_url: 'pkg:maven/example/library@1', scope: 'runtime', vulnerabilities: [], ...overrides,
});
const response = (changes = [], headers = {}) => ({ ok: true, headers: new Headers(headers), json: async () => changes });

test('a genuinely empty comparison is accepted only without snapshot warnings', async () => {
  const result = await verifyDependencyReview({ ...options, request: async () => response() });
  assert.deepEqual(result, { baseSha: options.baseSha, headSha: options.headSha, pages: 1, added: 0, removed: 0 });
  for (const changes of [[], [change()]]) {
    await assert.rejects(verifyDependencyReview({ ...options, request: async () => response(changes, {
      'x-github-dependency-graph-snapshot-warnings': Buffer.from('No snapshots were found for the head SHA').toString('base64'),
    }) }), /snapshot warnings remain/);
  }
});

test('all pages use the exact PR base and head, including later-page vulnerabilities', async () => {
  const request = async (url, init) => {
    assert.equal(init.redirect, 'error');
    assert.equal(init.headers.Authorization, 'Bearer test-token');
    if (url === `${endpoint}?per_page=100&page=1`) {
      return response([change()], { link: `<${endpoint}?per_page=100&page=2>; rel="next"` });
    }
    assert.equal(url, `${endpoint}?per_page=100&page=2`);
    return response([change({ vulnerabilities: [{ severity: 'high' }] })]);
  };
  await assert.rejects(verifyDependencyReview({ ...options, request }), /high or critical/);
});

test('a snapshot warning on a later page still blocks the result', async () => {
  let calls = 0;
  await assert.rejects(verifyDependencyReview({ ...options, request: async () => ++calls === 1
    ? response([change()], { link: `<${endpoint}?per_page=100&page=2>; rel="next"` })
    : response([], { 'x-github-dependency-graph-snapshot-warnings': Buffer.from('base snapshot unavailable').toString('base64') })
  }), /snapshot warnings remain/);
  assert.equal(calls, 2);
});

test('multiple or mixed-case Link relations cannot hide a later-page vulnerability', async () => {
  for (const relation of ['next alternate', 'NeXt']) {
    let calls = 0;
    await assert.rejects(verifyDependencyReview({ ...options, request: async () => ++calls === 1
      ? response([], { link: `<${endpoint}?per_page=100&page=2>; rel="${relation}"` })
      : response([change({ vulnerabilities: [{ severity: 'critical' }] })])
    }), /high or critical/);
    assert.equal(calls, 2);
  }
});

test('the existing severity and scope policy is preserved', async () => {
  for (const severity of ['high', 'critical']) {
    await assert.rejects(verifyDependencyReview({ ...options, request: async () => response([
      change({ scope: undefined, vulnerabilities: [{ severity }] }),
    ]) }), /high or critical/);
  }
  const result = await verifyDependencyReview({ ...options, request: async () => response([
    change({ vulnerabilities: [{ severity: 'moderate' }] }),
    change({ scope: 'development', vulnerabilities: [{ severity: 'high' }] }),
    change({ change_type: 'removed', vulnerabilities: [{ severity: 'critical' }] }),
  ]) });
  assert.equal(result.added, 2);
  assert.equal(result.removed, 1);
});

test('HTTP failures and malformed responses never become an empty pass', async () => {
  await assert.rejects(verifyDependencyReview({ ...options, request: async () => ({ ok: false, status: 403 }) }), /HTTP 403/);
  for (const data of [{}, null, [change({ vulnerabilities: 'unknown' })], [change({ vulnerabilities: [{ severity: 'unrecognized' }] })]]) {
    await assert.rejects(verifyDependencyReview({ ...options, request: async () => response(data) }), /Invalid dependency/);
  }
  await assert.rejects(verifyDependencyReview({ ...options, request: async () => response([], {
    'x-github-dependency-graph-snapshot-warnings': 'malformed-header',
  }) }), /Invalid dependency snapshot/);
});

test('unexpected pagination cannot redirect the token or skip pages', async () => {
  for (const link of [
    '<https://example.invalid/>; rel="next"',
    `<${endpoint}?per_page=100&page=3>; rel="next"`,
    `<${endpoint}?per_page=5&page=2>; rel="next"`,
  ]) {
    let calls = 0;
    await assert.rejects(verifyDependencyReview({ ...options, request: async () => {
      calls++;
      return response([], { link });
    } }), /Invalid dependency comparison pagination/);
    assert.equal(calls, 1);
  }
});

test('missing or non-exact commit inputs stop before a network request', async () => {
  for (const bad of [{ baseSha: 'main' }, { headSha: 'refs/pull/1/merge' }, { token: '' }, { repository: '../other/repo' }]) {
    await assert.rejects(verifyDependencyReview({ ...options, ...bad, request: () => assert.fail('must not request') }), /exact base\/head/);
  }
});
