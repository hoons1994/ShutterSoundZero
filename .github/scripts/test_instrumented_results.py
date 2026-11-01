import importlib.util
from pathlib import Path
import tempfile
import unittest
import subprocess
import sys

spec = importlib.util.spec_from_file_location('results', Path(__file__).with_name('verify-instrumented-results.py'))
results = importlib.util.module_from_spec(spec)
spec.loader.exec_module(results)


class ResultTests(unittest.TestCase):
    policy = {'Common': {'methods': ['works', 'alsoWorks']},
              'Network37': {'min_api': 37, 'methods': ['permission']},
              'Legacy': {'max_api': 33, 'methods': ['discovery']}}

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / 'TEST-device.xml'

    def write(self, extra='', replace='', api=35):
        xml = '<testsuite tests="2"><testcase classname="Common" name="works"/>'
        xml += '<testcase classname="Common" name="alsoWorks"/></testsuite>'
        xml = xml.replace('tests="2"', '').replace('</testsuite>', extra + '</testsuite>')
        self.path.write_text(replace or xml)
        return results.validate(self.tmp.name, api, self.policy)

    def test_normal_and_os_specific_required_methods(self):
        self.assertEqual(2, self.write()['executed'])
        for api in range(30, 38):
            extra = ('<testcase classname="Legacy" name="discovery"/>' if api <= 33 else
                     '<testcase classname="Network37" name="permission"/>' if api == 37 else '')
            self.assertGreaterEqual(self.write(extra=extra, api=api)['executed'], 2)

    def test_allowed_os_skips(self):
        self.assertEqual(1, self.write(extra='<testcase classname="Network37" name="permission"><skipped/></testcase>')['reported_skips'])

    def test_failure_and_error(self):
        for tag in ('failure', 'error'):
            with self.assertRaisesRegex(results.InvalidResults, 'TEST_FAILURE_OR_ERROR'):
                self.write(extra=f'<testcase classname="Extra" name="bad"><{tag}/></testcase>')

    def test_absent_empty_and_malformed(self):
        with self.assertRaisesRegex(results.InvalidResults, 'NO_RESULT_FILES'):
            results.validate(self.tmp.name, 35, self.policy)
        for xml, error in [('<testsuite/>', 'ZERO_EXECUTED'), ('<testsuite>', 'MALFORMED_XML'),
                           ('<!DOCTYPE x><testsuite/>', 'MALFORMED_XML')]:
            with self.assertRaisesRegex(results.InvalidResults, error):
                self.write(replace=xml)

    def test_missing_unexpected_skip_and_duplicate(self):
        for xml, error in [('<testsuite><testcase classname="Common" name="works"/></testsuite>', 'MISSING_REQUIRED'),
                           ('<testsuite><testcase classname="Common" name="works"/><testcase classname="Common" name="alsoWorks"><skipped/></testcase></testsuite>', 'UNEXPECTED_SKIP'),
                           ('<testsuite><testcase classname="Common" name="works"/><testcase classname="Common" name="works"/></testsuite>', 'DUPLICATE')]:
            with self.assertRaisesRegex(results.InvalidResults, error):
                self.write(replace=xml)

    def test_counter_mismatch_and_unknown_case(self):
        with self.assertRaisesRegex(results.InvalidResults, 'INCONSISTENT_COUNTS'):
            self.write(replace='<testsuite tests="10"><testcase classname="Common" name="works"/></testsuite>')
        with self.assertRaisesRegex(results.InvalidResults, 'UNEXPECTED_TESTS'):
            self.write(extra='<testcase classname="Unknown" name="x"/>')

    def test_policy_matches_current_test_source(self):
        policy = results.load_policy(Path(__file__).with_name('instrumented-test-policy.json'), 'app/src/androidTest/java')
        self.assertGreater(len(policy), 0)

    def test_all_skipped_is_not_an_execution(self):
        with self.assertRaisesRegex(results.InvalidResults, 'ZERO_EXECUTED'):
            self.write(replace='<testsuite><testcase classname="Common" name="works"><skipped/></testcase></testsuite>')

    def test_out_of_range_execution_is_rejected(self):
        with self.assertRaisesRegex(results.InvalidResults, 'OUT_OF_RANGE'):
            self.write(extra='<testcase classname="Network37" name="permission"/>')

    def test_no_report_distinguishes_prior_gradle_failure(self):
        for outcome, code in [('failure', 0), ('cancelled', 0), ('success', 1)]:
            process = subprocess.run([sys.executable, str(Path(__file__).with_name('verify-instrumented-results.py')),
                                      '--results', self.tmp.name, '--api', '35', '--gradle-outcome', outcome],
                                     capture_output=True)
            self.assertEqual(code, process.returncode)


if __name__ == '__main__':
    unittest.main()
