"""Require independent JVM XML/HTML and Lint reports after successful Gradle validation."""
import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def verify(root, variants):
    root = Path(root)
    for variant in variants:
        task = f'test{variant.title()}UnitTest'
        files = list((root / 'test-results' / task).glob('TEST-*.xml'))
        if not files:
            raise ValueError(f'Missing JVM XML: {task}')
        total = 0
        for path in files:
            tree = ET.parse(path)
            cases = list(tree.iter('testcase'))
            total += len(cases)
            if any(c.find(tag) is not None for c in cases for tag in ('error', 'failure', 'skipped')):
                raise ValueError(f'Failed or skipped JVM tests: {path}')
        if total == 0:
            raise ValueError(f'Zero JVM tests: {task}')
        for path in [root / 'reports/tests' / task / 'index.html',
                     root / f'reports/lint-results-{variant}.html',
                     root / f'reports/lint-results-{variant}.xml']:
            if not path.is_file() or path.stat().st_size == 0:
                raise ValueError(f'Missing report: {path}')
        ET.parse(root / f'reports/lint-results-{variant}.xml')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', default='app/build')
    parser.add_argument('--variants', nargs='+', default=['debug'])
    parser.add_argument('--gradle-outcome', required=True)
    args = parser.parse_args()
    try:
        verify(args.root, args.variants)
        print('JVM XML/HTML and Lint XML/HTML reports verified')
    except (ValueError, OSError, ET.ParseError) as error:
        if args.gradle_outcome == 'success':
            sys.exit(f'Reports missing/invalid after successful validation: {error}')
        print(f'::warning::Reports unavailable/incomplete after Gradle {args.gradle_outcome}; preserve original failure: {error}')
