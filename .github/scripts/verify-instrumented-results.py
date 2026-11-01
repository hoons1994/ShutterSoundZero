"""Fail closed on missing/partial JUnit results; SDK exclusions are explicit policy."""
import argparse
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET


class InvalidResults(ValueError):
    pass


def source_tests(source):
    found = {}
    for path in sorted(Path(source).rglob('*')):
        if path.suffix not in ('.kt', '.java'):
            continue
        text = path.read_text(encoding='utf-8-sig')
        methods = re.findall(r'@Test(?:\([^)]*\))?\s+(?:@\w+(?:\([^)]*\))?\s+)*(?:fun|public\s+void)\s+(\w+)\s*\(', text)
        if not methods:
            continue
        # Reject unfamiliar annotations/signatures rather than silently shrinking coverage.
        if len(methods) != len(re.findall(r'@Test\b', text)):
            raise InvalidResults(f'Unrecognized @Test declaration: {path}')
        package = re.search(r'^package\s+([\w.]+)', text, re.M).group(1)
        found[f'{package}.{path.stem}'] = sorted(methods)
    return found


def load_policy(path, source):
    policy = json.loads(Path(path).read_text(encoding='utf-8'))
    if {name: sorted(entry['methods']) for name, entry in policy.items()} != source_tests(source):
        raise InvalidResults('Test inventory drift: review instrumented-test-policy.json against source')
    for name, entry in policy.items():
        if not entry['methods'] or len(set(entry['methods'])) != len(entry['methods']):
            raise InvalidResults(f'Empty/duplicate policy methods: {name}')
        if not 30 <= entry.get('min_api', 30) <= entry.get('max_api', 37) <= 37:
            raise InvalidResults(f'Invalid API range: {name}')
    return policy


def validate(directory, api, policy):
    if api not in range(30, 38):
        raise InvalidResults(f'Unsupported API: {api}')
    files = sorted(Path(directory).rglob('TEST-*.xml'))
    if not files:
        raise InvalidResults('NO_RESULT_FILES: no JUnit XML')
    cases = {}
    for path in files:
        data = path.read_bytes()
        if b'<!DOCTYPE' in data.upper() or b'<!ENTITY' in data.upper():
            raise InvalidResults(f'MALFORMED_XML: declarations forbidden: {path}')
        try:
            root = ET.fromstring(data)
        except ET.ParseError as error:
            raise InvalidResults(f'MALFORMED_XML: {path}: {error}') from error
        if root.tag not in ('testsuites', 'testsuite'):
            raise InvalidResults(f'MALFORMED_XML: unexpected root: {path}')
        for suite in root.iter():
            if suite.tag not in ('testsuites', 'testsuite'):
                continue
            nested = list(suite.iter('testcase'))
            expected = {'tests': len(nested),
                        'failures': sum(c.find('failure') is not None for c in nested),
                        'errors': sum(c.find('error') is not None for c in nested),
                        'skipped': sum(c.find('skipped') is not None for c in nested)}
            for key, actual in expected.items():
                if key in suite.attrib and suite.attrib[key] != str(actual):
                    raise InvalidResults(f'INCONSISTENT_COUNTS: {path}: {key}')
        for case in root.iter('testcase'):
            key = (case.get('classname'), case.get('name'))
            if not all(key) or key in cases:
                raise InvalidResults(f'DUPLICATE_OR_INVALID_TEST: {key}')
            if case.find('failure') is not None or case.find('error') is not None:
                raise InvalidResults(f'TEST_FAILURE_OR_ERROR: {key}')
            cases[key] = case.find('skipped') is not None
    if not cases or all(cases.values()):
        raise InvalidResults('ZERO_EXECUTED_TESTS')
    required, excluded = set(), set()
    for name, entry in policy.items():
        target = required if entry.get('min_api', 30) <= api <= entry.get('max_api', 37) else excluded
        target.update((name, method) for method in entry['methods'])
    unknown = cases.keys() - (required | excluded)
    if unknown:
        raise InvalidResults(f'UNEXPECTED_TESTS: {sorted(unknown)}')
    missing = required - cases.keys()
    if missing:
        raise InvalidResults(f'MISSING_REQUIRED_TESTS: {sorted(missing)}')
    skipped = {key for key in required if cases[key]}
    if skipped:
        raise InvalidResults(f'UNEXPECTED_SKIP: {sorted(skipped)}')
    wrongly_run = {key for key in excluded & cases.keys() if not cases[key]}
    if wrongly_run:
        raise InvalidResults(f'OUT_OF_RANGE_TEST_EXECUTED: {sorted(wrongly_run)}')
    return {'api': api, 'executed': len(required), 'allowed_os_exclusions': len(excluded),
            'reported_skips': sum(cases.values()), 'files': len(files)}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--results', required=True)
    parser.add_argument('--api', type=int, required=True)
    parser.add_argument('--policy', default='.github/scripts/instrumented-test-policy.json')
    parser.add_argument('--source', default='app/src/androidTest/java')
    parser.add_argument('--gradle-outcome', default='success', choices=['success', 'failure', 'cancelled'])
    args = parser.parse_args()
    try:
        print(json.dumps(validate(args.results, args.api, load_policy(args.policy, args.source))))
    except (InvalidResults, OSError, ValueError, KeyError) as error:
        if args.gradle_outcome != 'success' and str(error).startswith('NO_RESULT_FILES:'):
            print(f'::warning::No XML after Gradle {args.gradle_outcome}; original failure remains authoritative')
            parser.exit(0)
        parser.exit(1, f'Instrumented result validation failed: {error}\n')
