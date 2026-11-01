import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('verify-build-reports.py')
spec = importlib.util.spec_from_file_location('reports', SCRIPT)
reports = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reports)


class ReportTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def fixture(self):
        for name, text in {
            'test-results/testDebugUnitTest/TEST-example.xml': '<testsuite><testcase name="works"/></testsuite>',
            'reports/tests/testDebugUnitTest/index.html': '<html>tests</html>',
            'reports/lint-results-debug.xml': '<issues/>',
            'reports/lint-results-debug.html': '<html>lint</html>',
        }.items():
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)

    def test_complete_reports(self):
        self.fixture()
        reports.verify(self.root, ['debug'])

    def test_html_does_not_substitute_for_xml(self):
        self.fixture()
        next((self.root / 'test-results').rglob('*.xml')).unlink()
        with self.assertRaisesRegex(ValueError, 'Missing JVM XML'):
            reports.verify(self.root, ['debug'])

    def test_each_lint_and_html_report_is_required(self):
        for relative in ['reports/lint-results-debug.xml', 'reports/lint-results-debug.html',
                         'reports/tests/testDebugUnitTest/index.html']:
            self.fixture()
            (self.root / relative).unlink()
            with self.assertRaisesRegex(ValueError, 'Missing report'):
                reports.verify(self.root, ['debug'])

    def test_failed_or_skipped_jvm_tests(self):
        for tag in ['failure', 'error', 'skipped']:
            self.fixture()
            next((self.root / 'test-results').rglob('*.xml')).write_text(
                f'<testsuite><testcase name="bad"><{tag}/></testcase></testsuite>')
            with self.assertRaisesRegex(ValueError, 'Failed or skipped'):
                reports.verify(self.root, ['debug'])

    def test_missing_report_after_failure_preserves_original_failure(self):
        for outcome, code in [('failure', 0), ('cancelled', 0), ('success', 1)]:
            result = subprocess.run([sys.executable, str(SCRIPT), '--root', str(self.root),
                                     '--gradle-outcome', outcome], capture_output=True)
            self.assertEqual(code, result.returncode)


if __name__ == '__main__':
    unittest.main()
