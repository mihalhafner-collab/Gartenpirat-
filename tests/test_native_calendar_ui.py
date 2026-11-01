"""Static native calendar presentation guards. Device acceptance remains NOT EXECUTED."""
import hashlib
import json
import re
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/at/hausgarten/service'
PROOF=json.loads((ROOT/'tests/fixtures/native-calendar-presentation-baseline.json').read_text())

def method(source,name):
    match=re.search(r'^    (?:@Override )?(?:private|public|protected) [^\n]*?\b'+name+r'\([^\n]*?\)\s*\{',source,re.M)
    if not match:raise AssertionError('Missing protected method '+name)
    depth=0;quote=None;escape=False
    for i in range(source.index('{',match.start()),len(source)):
        c=source[i]
        if quote:
            if escape:escape=False
            elif c=='\\':escape=True
            elif c==quote:quote=None
            continue
        if c in ('"',"'"):quote=c
        elif c=='{':depth+=1
        elif c=='}':
            depth-=1
            if depth==0:return source[match.start():i+1]
    raise AssertionError('Unclosed protected method '+name)

class NativeCalendarUiTests(unittest.TestCase):
    def test_original_primary_account_and_intent_methods_are_byte_identical(self):
        source=(JAVA/'PlanningConnectionActivity.java').read_text()
        for name,digest in PROOF['protected_method_sha256'].items():
            self.assertEqual(hashlib.sha256(method(source,name).encode()).hexdigest(),digest,name)
    def test_diagnostics_and_account_stay_in_separate_closed_disclosures(self):
        s=(JAVA/'PlanningConnectionActivity.java').read_text()
        self.assertRegex(s,r'disclosure\("Konto und Freigaben"\);root=accountDetails;accountLabel=text\(""\)')
        self.assertRegex(s,r'disclosure\("Technische Details"\);root=technicalDetails;connectionDetails=NativeUi.note\(text\(""\)\)')
        self.assertIn('body.setVisibility(android.view.View.GONE)',s)
        self.assertEqual(s.count('CalendarAccessRules.message('),1)
        self.assertIn('calendarFailure.setText(setupCode.isEmpty()?"":"! "+CalendarAccessRules.message(setupCode))',s)
        self.assertIn('"Prüfschritt: "+CalendarAccessRules.phaseLabel',s)
        self.assertIn('"\\nHinweiscode: "+CalendarAccessRules.safeCode(code)',s)
        header=s[s.index('calendarStatus.setText(needsReview'):s.index('accountLabel.setText(connected')]
        self.assertNotIn('safeCode',header);self.assertNotIn('Prüfschritt',header)
    def test_connection_actions_and_confirmation_keep_safe_presentation(self):
        s=(JAVA/'PlanningConnectionActivity.java').read_text()
        for fragment in ['button("Verbinden",this::calendarPrimary)','"Erneut verbinden"','NativeUi.style(disconnectButton,2)','Nur Verbindung trennen? App-Termine und Google-Einträge bleiben erhalten.','setNegativeButton("Abbrechen",null).setPositiveButton("Trennen",(d,w)->disconnect())']:
            self.assertIn(fragment,s)
        literals=re.findall(r'"(?:\\.|[^"\\])*"',method(s,'updateCalendarStatus'))
        for literal in literals:
            self.assertNotRegex(literal,r'[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}|Bearer |Authorization:|ya29\.')
    def test_active_screen_preferences_remain_frozen_and_drive_redirect_cannot_write(self):
        pattern=r'\.(?:putString|putBoolean|putInt|putLong|remove|putOwnerValue)\([^;\n]*?\)'
        for name,digests in PROOF['preference_write_sha256'].items():
            source=(JAVA/(name+'.java')).read_text()
            actual=sorted(hashlib.sha256(x.encode()).hexdigest() for x in re.findall(pattern,source))
            if name=='DriveConnectionActivity':
                self.assertEqual(actual,[],name)
                self.assertIn('startActivity(new Intent(this,FullBackupActivity.class));',source)
                self.assertIn('finish();',source)
                for retired in ['getSharedPreferences(','.edit(', 'DriveRestClient', 'DriveAccount', 'ACTION_OPEN_DOCUMENT_TREE']:
                    self.assertNotIn(retired,source)
            else:
                self.assertEqual(actual,digests,name)
