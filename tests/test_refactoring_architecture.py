"""Protect actual P1 dependency boundaries, alongside behavioral contract tests."""
from pathlib import Path
import re
import unittest

JAVA = Path(__file__).resolve().parents[1]/'android/app/src/main/java/at/hausgarten/service'


class RefactoringArchitectureTests(unittest.TestCase):
    def test_application_services_and_ports_have_no_android_sql_or_router_dependency(self):
        sources = list(JAVA.glob('*ApplicationService.java')) + [JAVA/'OwnerRepository.java', JAVA/'OwnerCommand.java']
        self.assertGreaterEqual(len(sources), 10)
        for path in sources:
            with self.subTest(source=path.name):
                text = path.read_text()
                self.assertNotRegex(text, r'import android\.|MainActivity|SQLiteDatabase|\.rawQuery\(')
                if path.name != 'OwnerApplicationService.java':
                    self.assertNotIn('OwnerApplicationService.', text)

    def test_mcp_boundaries_do_not_open_sql(self):
        for name in ('PixelMcpReadTools', 'PixelMcpOwnerTools', 'PixelOwnerAccess', 'NativeOwnerQueries'):
            with self.subTest(source=name):
                self.assertNotRegex((JAVA/(name+'.java')).read_text(), r'\.(?:rawQuery|insertOrThrow|insertWithOnConflict|delete)\(')

    def test_one_store_without_activity_or_tunnel_type_checks(self):
        activity = (JAVA/'MainActivity.java').read_text()
        store = (JAVA/'PixelLocalStore.java').read_text()
        self.assertNotIn('class LocalStore', activity)
        self.assertNotIn('MainActivity', store)
        self.assertNotIn('PixelTunnelService', store)
        self.assertIn('context instanceof McpSyncHost', store)
        self.assertEqual(len(re.findall('extends SQLiteOpenHelper', store)), 1)

    def test_snapshot_projection_is_independent_of_transport(self):
        text = (JAVA/'SnapshotProjection.java').read_text()
        self.assertNotRegex(text, r'android\.|PixelMcpReadTools|MainActivity|rawQuery|tools/call')

    def test_business_bridge_owns_no_lifecycle_or_platform_dialogs(self):
        text = (JAVA/'BusinessDataBridge.java').read_text()
        self.assertNotRegex(text, r'MainActivity|AlertDialog|startActivity|\.rawQuery\(|\.beginTransaction\(')
        self.assertIn('public class BusinessDataBridge', text)
        self.assertIn('DataBridge extends BusinessDataBridge', (JAVA/'MainActivity.java').read_text())
