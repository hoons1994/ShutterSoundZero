import { pathToFileURL } from 'node:url';

// These immutable documentation heads inherit the same analyzed code, not a fabricated check run.
export const baseline = '83148cb7187866a9b7fafd9f62b23c69e369a290';
export const legacyHeads = new Map([
  [190, '167ab2109b6abb4ee0aabea5fd13b0dc90f0df1d'],
  [191, 'c37a20c8bbc254dd5ee1ce56547298201fa46145'],
]);
const documentation = new Set(['README.md', '.github/release-notes/v1.5.8.md', '.github/release-notes/v1.5.9.md']);
const sastApps = new Set(['github-advanced-security', 'github-code-scanning', 'lgtm-com', 'sonarcloud', 'sonarqubecloud']);
const shaPattern = /^[0-9a-f]{40}$/;
const checkFields = `checkSuites(first:30) { pageInfo { hasNextPage } nodes {
  app { slug } checkRuns(first:100) { pageInfo { hasNextPage } nodes { name status conclusion } }
} }`;
const pageSize = 5;
const historyLimit = 30;
export const query = `query($owner:String!, $name:String!, $after:String, $firstPage:Boolean!) {
  repository(owner:$owner,name:$name) {
    defaultBranchRef { target { ... on Commit { oid history(first:${pageSize},after:$after) { totalCount pageInfo { hasNextPage endCursor } nodes {
      oid associatedPullRequests(first:1) { nodes {
        number mergedAt headRefOid commits(last:1) { nodes { commit { oid ${checkFields} } } }
      } }
    } } } } }
    baseline:object(oid:"${baseline}") @include(if:$firstPage) { ... on Commit { oid ${checkFields} } }
  }
}`;

function checked(commit) {
  if (!commit || !shaPattern.test(commit.oid)) throw new Error('Invalid SAST commit');
  const suites = commit.checkSuites;
  if (!Array.isArray(suites?.nodes) || suites.pageInfo?.hasNextPage !== false) {
    throw new Error('Incomplete SAST check-suite response');
  }
  let passed = false;
  for (const suite of suites.nodes) {
    const runs = suite.checkRuns;
    if (!Array.isArray(runs?.nodes) || runs.pageInfo?.hasNextPage !== false) {
      throw new Error('Incomplete SAST check-run response');
    }
    if (sastApps.has(suite.app?.slug) && runs.nodes.some(run =>
      run.status === 'COMPLETED' && ['SUCCESS', 'NEUTRAL'].includes(run.conclusion))) passed = true;
  }
  return passed;
}

export function verifyDocumentationComparison(comparison, head) {
  if (comparison?.base_commit?.sha !== baseline || comparison?.merge_base_commit?.sha !== baseline ||
      comparison?.commits?.at(-1)?.sha !== head || !Array.isArray(comparison.files) ||
      comparison.files.length < 1 || comparison.files.length > documentation.size ||
      comparison.files.some(file => file.status !== 'modified' || file.previous_filename ||
        !documentation.has(file.filename))) {
    throw new Error('Legacy exception is not an unchanged analyzed codebase');
  }
}

export async function verifySastCoverage({ repository, token, request = fetch }) {
  if (!/^[\w.-]+\/[\w.-]+$/.test(repository ?? '') || !token) throw new Error('Repository and token are required');
  const headers = { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json',
    'Content-Type': 'application/json',
    'X-GitHub-Api-Version': '2022-11-28' };
  async function json(url, options = {}) {
    const response = await request(url, { ...options, headers, redirect: 'error', signal: AbortSignal.timeout(30_000) });
    if (!response.ok) throw new Error(`SAST API HTTP ${response.status}`);
    return response.json();
  }
  const [owner, name] = repository.split('/');
  const commits = [];
  const cursors = new Set();
  let after = null, tip, totalCount, baselineCommit;
  do {
    const firstPage = after === null;
    const result = await json('https://api.github.com/graphql', {
      method: 'POST', body: JSON.stringify({ query, variables: { owner, name, after, firstPage } }),
    });
    const repo = result?.data?.repository;
    const target = repo?.defaultBranchRef?.target;
    const history = target?.history;
    if (result?.errors?.length || !shaPattern.test(target?.oid ?? '') ||
        !Number.isSafeInteger(history?.totalCount) || history.totalCount < 1 ||
        !Array.isArray(history.nodes) || history.nodes.length !== Math.min(pageSize, history.totalCount - commits.length)) {
      throw new Error('Incomplete SAST history response');
    }
    if (firstPage) {
      tip = target.oid;
      totalCount = history.totalCount;
      baselineCommit = repo.baseline;
      if (history.nodes[0]?.oid !== tip) throw new Error('Incomplete SAST history at branch tip');
    } else if (tip !== target.oid || totalCount !== history.totalCount) {
      throw new Error('SAST history changed during pagination');
    }
    commits.push(...history.nodes);
    if (history.pageInfo?.hasNextPage !== (commits.length < totalCount)) throw new Error('Incomplete SAST history page');
    if (commits.length >= Math.min(historyLimit, totalCount)) break;
    after = history.pageInfo.endCursor;
    if (typeof after !== 'string' || !after || cursors.has(after)) throw new Error('Incomplete SAST history cursor');
    cursors.add(after);
  } while (true);
  if (new Set(commits.map(commit => commit.oid)).size !== commits.length) throw new Error('Duplicate SAST history entry');
  const comparisons = new Map();
  let direct = 0, inherited = 0, total = 0;
  for (const commit of commits) {
    if (!shaPattern.test(commit.oid) || !Array.isArray(commit.associatedPullRequests?.nodes)) {
      throw new Error('Invalid SAST history entry');
    }
    const pr = commit.associatedPullRequests.nodes[0];
    if (!pr?.mergedAt) continue; // Same merged-PR scope as OpenSSF Scorecard.
    const head = pr.commits?.nodes?.[0]?.commit;
    if (!head || head.oid !== pr.headRefOid) throw new Error('PR head does not match SAST evidence');
    total++;
    if (checked(head)) { direct++; continue; }
    if (legacyHeads.get(pr.number) !== head.oid || baselineCommit?.oid !== baseline || !checked(baselineCommit)) {
      throw new Error(`Missing successful SAST for PR #${pr.number} at ${head.oid}`);
    }
    if (!comparisons.has(head.oid)) {
      const comparison = await json(`https://api.github.com/repos/${repository}/compare/${baseline}...${head.oid}?per_page=100`);
      verifyDocumentationComparison(comparison, head.oid);
      comparisons.set(head.oid, true);
    }
    inherited++;
  }
  if (!total) throw new Error('No merged-PR SAST evidence found');
  return { total, direct, inherited, legacyPullRequests: comparisons.size };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const result = await verifySastCoverage({ repository: process.env.GITHUB_REPOSITORY, token: process.env.GH_TOKEN });
    console.log(`Verified SAST: ${result.direct}/${result.total} direct; ${result.inherited} inherited from identical analyzed code in ${result.legacyPullRequests} documented legacy PR(s).`);
  } catch (error) {
    console.error(`SAST coverage verification failed: ${error.message}`);
    process.exitCode = 1;
  }
}
