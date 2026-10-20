import assert from 'node:assert/strict';
import test from 'node:test';
import { baseline, legacyHeads, verifyDocumentationComparison, verifySastCoverage } from './verify-sast-coverage.mjs';

const head = legacyHeads.get(190);
const suites = (app = 'github-code-scanning', conclusion = 'SUCCESS') => ({
  pageInfo: { hasNextPage: false }, nodes: [{ app: { slug: app }, checkRuns: {
    pageInfo: { hasNextPage: false }, nodes: [{ status: 'COMPLETED', conclusion }],
  } }],
});
const entry = (number, sha, checks = suites()) => ({
  oid: sha, associatedPullRequests: { nodes: [{ number, mergedAt: '2026-10-01T00:00:00Z', headRefOid: sha,
    commits: { nodes: [{ commit: { oid: sha, checkSuites: checks } }] } }] },
});
const comparison = () => ({ base_commit: { sha: baseline }, merge_base_commit: { sha: baseline },
  commits: [{ sha: head }], files: [{ filename: '.github/release-notes/v1.5.9.md', status: 'modified' }] });
const options = (entries, compare = comparison(), baselineChecks = suites(), totalCount = entries.length) => ({
  repository: 'owner/repo', token: 'test', request: async url => ({ ok: true, json: async () =>
    url.endsWith('/graphql') ? { data: { repository: {
      defaultBranchRef: { target: { history: { nodes: entries, totalCount } } },
      baseline: { oid: baseline, checkSuites: baselineChecks },
    } } } : compare }),
});

test('successful recognized SAST is required; ordinary CI is not substituted', async () => {
  assert.deepEqual(await verifySastCoverage(options([entry(214, 'a'.repeat(40))])),
    { total: 1, direct: 1, inherited: 0, legacyPullRequests: 0 });
  for (const checks of [suites('github-actions'), suites('github-code-scanning', 'FAILURE'),
    { pageInfo: { hasNextPage: false }, nodes: [] }]) {
    await assert.rejects(verifySastCoverage(options([entry(214, 'a'.repeat(40), checks)])), /Missing successful SAST/);
  }
});

test('only immutable legacy heads with successful baseline and documentation-only comparison inherit evidence', async () => {
  const missing = suites('github-actions');
  assert.equal((await verifySastCoverage(options([entry(190, head, missing)]))).inherited, 1);
  await assert.rejects(verifySastCoverage(options([entry(190, 'b'.repeat(40), missing)])), /Missing successful SAST/);
  await assert.rejects(verifySastCoverage(options([entry(190, head, missing)], comparison(), missing)), /Missing successful SAST/);
});

test('code changes, renames, unknown files, or an unrelated merge base cannot use the documentation exception', () => {
  for (const change of [
    { files: [{ filename: 'app/src/main/Main.kt', status: 'modified' }] },
    { files: [{ filename: 'README.md', status: 'renamed', previous_filename: 'app/Main.kt' }] },
    { files: [{ filename: '.github/workflows/codeql.yml', status: 'modified' }] },
    { files: [] }, { merge_base_commit: { sha: 'b'.repeat(40) } }, { commits: [{ sha: 'b'.repeat(40) }] },
  ]) assert.throws(() => verifyDocumentationComparison({ ...comparison(), ...change }, head));
});

test('API failure, GraphQL errors, or incomplete pagination cannot produce a green result', async () => {
  await assert.rejects(verifySastCoverage({ repository: 'owner/repo', token: 'test', request: async () => ({ ok: false, status: 403 }) }), /HTTP 403/);
  await assert.rejects(verifySastCoverage({ repository: 'owner/repo', token: 'test', request: async () => ({ ok: true, json: async () => ({ errors: [{ message: 'denied' }] }) }) }), /Incomplete/);
  const incomplete = suites(); incomplete.pageInfo.hasNextPage = true;
  await assert.rejects(verifySastCoverage(options([entry(214, 'a'.repeat(40), incomplete)])), /Incomplete/);
  const moreRuns = suites(); moreRuns.nodes[0].checkRuns.pageInfo.hasNextPage = true;
  await assert.rejects(verifySastCoverage(options([entry(214, 'a'.repeat(40), moreRuns)])), /Incomplete/);
  await assert.rejects(verifySastCoverage(options([entry(214, 'a'.repeat(40))], comparison(), suites(), 30)), /Incomplete/);
});

test('changed PR identity and empty history fail closed', async () => {
  const changed = entry(214, 'a'.repeat(40));
  changed.associatedPullRequests.nodes[0].headRefOid = 'b'.repeat(40);
  await assert.rejects(verifySastCoverage(options([changed])), /head does not match/);
  await assert.rejects(verifySastCoverage(options([])), /Incomplete/);
});
