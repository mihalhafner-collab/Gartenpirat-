"""Current hook behavior with synthetic files; no model, network or operating data."""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

REPOSITORY = Path(__file__).resolve().parents[2]
SCRIPT = REPOSITORY / '.codex/read_project_context.py'
spec = importlib.util.spec_from_file_location('project_context_hook', SCRIPT)
hook = importlib.util.module_from_spec(spec)
spec.loader.exec_module(hook)


class ProjectContextHookTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='qa_project_hook_')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'struktur-neu').mkdir()
        (self.root / 'AGENTS.md').write_text('KISS und YAGNI; erste Regel.\n')
        self.state = self.root / 'struktur-neu/AKTUELLER-STAND.json'
        self.state.write_text('{"status":"qa_first"}\n')
        self.payload = {'hook_event_name': 'UserPromptSubmit', 'prompt': 'qa_private_prompt_never_echo'}

    def context(self):
        return hook.handle(self.payload, self.root)['hookSpecificOutput']['additionalContext']

    def test_supplies_both_complete_files_with_actual_hashes_and_no_prompt_echo(self):
        context = self.context()
        for name in hook.FILES:
            raw = (self.root / name).read_bytes()
            self.assertIn(raw.decode(), context)
            self.assertIn(hashlib.sha256(raw).hexdigest(), context)
        self.assertNotIn(self.payload['prompt'], context)

    def test_next_prompt_reads_changed_rules_instead_of_a_cache(self):
        before = self.context()
        (self.root / 'AGENTS.md').write_text('KISS; zweite Regel statt erster.\n')
        after = self.context()
        self.assertNotEqual(before, after)
        self.assertIn('zweite Regel', after)
        self.assertNotIn('erste Regel', after)

    def test_next_prompt_reads_changed_state(self):
        before = self.context()
        self.state.write_text('{"status":"qa_second"}')
        self.assertNotEqual(before, self.context())
        self.assertIn('qa_second', self.context())

    def test_missing_rules_or_state_explicitly_blocks(self):
        for name in hook.FILES:
            path = self.root / name
            raw = path.read_bytes()
            path.unlink()
            result = hook.handle(self.payload, self.root)
            self.assertEqual(result['decision'], 'block')
            self.assertNotIn('hookSpecificOutput', result)
            path.write_bytes(raw)

    def test_empty_rules_invalid_json_and_non_object_state_block(self):
        for bad in ('', '{broken', '[]'):
            self.state.write_text(bad)
            self.assertEqual(hook.handle(self.payload, self.root)['decision'], 'block')
        self.state.write_text('{}')
        (self.root / 'AGENTS.md').write_text('   ')
        self.assertEqual(hook.handle(self.payload, self.root)['decision'], 'block')

    def test_oversized_context_blocks_without_partial_context(self):
        (self.root / 'AGENTS.md').write_text('x' * hook.MAX_CONTEXT_BYTES)
        result = hook.handle(self.payload, self.root)
        self.assertEqual(result['decision'], 'block')
        self.assertNotIn('hookSpecificOutput', result)

    def test_symlink_outside_project_is_not_read(self):
        outside = self.root.parent / (self.root.name + '_qa_outside')
        outside.write_text('qa_outside_not_to_be_injected')
        self.addCleanup(outside.unlink)
        self.state.unlink()
        self.state.symlink_to(outside)
        result = hook.handle(self.payload, self.root)
        self.assertEqual(result['decision'], 'block')
        self.assertNotIn('qa_outside', json.dumps(result))

    def test_invalid_hook_input_and_malformed_stdin_block(self):
        for payload in (None, [], {}, {'hook_event_name': 'OtherEvent'}):
            self.assertEqual(hook.handle(payload, self.root)['decision'], 'block')
        result = subprocess.run([sys.executable, '-B', str(SCRIPT)], input='{broken', text=True, capture_output=True, check=True)
        self.assertEqual(json.loads(result.stdout)['decision'], 'block')


if __name__ == '__main__':
    unittest.main()
